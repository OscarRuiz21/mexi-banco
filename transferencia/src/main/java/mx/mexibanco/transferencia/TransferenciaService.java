package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSACION_PENDIENTE;
import static mx.mexibanco.transferencia.EstadoTransferencia.COMPENSADA;

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco. En el monolito (v05.1) este metodo era
 * UNA transaccion local: cargo, abono, asientos y avisos se confirmaban juntos o ninguno. En v06a
 * cada paso paso a ser una peticion HTTP a otro servicio que se confirma sola, y si el abono fallaba
 * despues del cargo, el dinero se quedaba en el aire.
 *
 * v08: este servicio es el ORQUESTADOR de una saga. Sigue sin @Transactional alrededor de las
 * llamadas (no hay transaccion que abarque tres procesos y cuatro bases); en su lugar:
 *
 *  - La transferencia tiene un estado (EstadoTransferencia) y se guarda en la base en CADA paso,
 *    cada save() en su propia transaccion local. Si el proceso se cae a la mitad, la fila dice en
 *    que paso se quedo.
 *  - Cada paso tiene su compensacion. El cargo se compensa con una DEVOLUCION (un abono a origen
 *    del mismo monto), que queda en el ledger como movimiento DEVOLUCION_TRANSFERENCIA. No se borra
 *    nada: deshacer es otra operacion de negocio.
 *  - El abono es el punto sin retorno: despues de el ya no se compensa, se termina.
 *  - Si la devolucion tambien falla, la fila queda en COMPENSACION_PENDIENTE y ReintentoDeCompensaciones
 *    la termina en cuanto cuenta vuelva.
 *
 * Las llamadas a cuenta pasan ademas por un circuit breaker (ver CuentaCliente).
 */
@Service
public class TransferenciaService {

	private static final Logger log = LoggerFactory.getLogger(TransferenciaService.class);

	private final TransferenciaRepository transferencias;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;
	private final long pausaEntreCargoYAbonoMs;
	private final String fallarAbonoAClabe;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaCliente cuentas,
								MovimientoCliente movimientos, NotificacionCliente notificaciones,
								@Value("${mexibanco.demo.pausa-entre-cargo-y-abono-ms:0}") long pausaEntreCargoYAbonoMs,
								@Value("${mexibanco.demo.fallar-abono-a-clabe:}") String fallarAbonoAClabe) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
		this.pausaEntreCargoYAbonoMs = pausaEntreCargoYAbonoMs;
		this.fallarAbonoAClabe = fallarAbonoAClabe == null ? "" : fallarAbonoAClabe.trim();
	}

	/**
	 * Corre la saga completa. Regresa la transferencia en COMPLETADA, COMPENSADA o
	 * COMPENSACION_PENDIENTE (el controller decide que le contesta al cliente en cada caso).
	 * Lanza excepcion si se rechaza antes de mover dinero (misma cuenta, CLABE inexistente, cargo
	 * rechazado) o si falla algo despues del abono.
	 */
	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		// Primero que existan las dos, luego el saldo: el mismo orden de errores que en el monolito.
		// Ojo: entre esta comprobacion y el cargo pasa tiempo de red; ya no es una foto consistente.
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		// La fila nace ANTES de tocar dinero: desde aqui la base sabe que esta transferencia existe.
		Transferencia t = guardar(new Transferencia(claveOrigen, claveDestino, monto));

		// Paso 1, el cargo. Si cuenta lo rechaza (422 saldo insuficiente) o no contesta, no hay nada que
		// compensar: queda FALLIDA y el cliente recibe el mismo error de siempre.
		// Lo que esta version NO cubre: un timeout de LECTURA en el cargo no prueba que no ocurrio (cuenta
		// pudo cargarlo y no alcanzar a contestar). Lo correcto seria preguntarle a cuenta por la clave
		// transferencia-N-cargo antes de darlo por fallido.
		CuentaRemota origen;
		try {
			origen = cuentas.cargar(claveOrigen, monto, t.clave("cargo"));
		} catch (RuntimeException cargoRechazado) {
			t.marcarFallida("El cargo a la CLABE " + claveOrigen + " no se hizo: " + cargoRechazado.getMessage());
			guardar(t);
			throw cargoRechazado;
		}
		t.marcarCargada(origen.saldo());
		t = guardar(t); // CARGADA: el dinero salio de origen y todavia no llega a destino

		pausaDeDemo();

		// Paso 2, el abono (el punto sin retorno). Si falla, se compensa el paso 1.
		CuentaRemota destino;
		try {
			destino = abonarADestino(t);
		} catch (RuntimeException abonoFallido) {
			log.warn("Transferencia {}: el abono fallo ({}); se devuelve el cargo", t.getId(), abonoFallido.getMessage());
			return compensar(t, "El abono a la CLABE " + claveDestino + " fallo: " + abonoFallido.getMessage());
		}
		t.marcarAbonada();
		t = guardar(t);

		// Paso 3, despues del pivote: asientos y avisos. El dinero ya esta donde debe, asi que si esto
		// falla NO se compensa: la fila se queda en ABONADA (la base dice que falta) y el cliente recibe
		// el error. Terminarlo sin que el cliente espere a notificacion es justo lo que resuelve pasar
		// los avisos a mensajeria asincrona; aqui sigue siendo HTTP sincrono, como en v06.
		MovimientoRemoto cargo = movimientos.registrar(t.clave("asiento-cargo"), claveOrigen,
			TipoMovimiento.TRANSFERENCIA_ENVIADA, monto.negate(), t.getSaldoOrigenTrasCargo(), "a " + claveDestino);
		MovimientoRemoto abono = movimientos.registrar(t.clave("asiento-abono"), claveDestino,
			TipoMovimiento.TRANSFERENCIA_RECIBIDA, monto, destino.saldo(), "de " + claveOrigen);
		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);

		t.marcarCompletada();
		return guardar(t);
	}

	/**
	 * La compensacion del cargo: devolverle el monto a origen y dejarlo escrito en el ledger (el cargo
	 * que salio y la devolucion que regreso, para que el estado de cuenta cuadre con el saldo).
	 * Si sale bien, COMPENSADA. Si no, COMPENSACION_PENDIENTE: nunca se queda en CARGADA.
	 *
	 * POR QUE ES IDEMPOTENTE (se puede reintentar sin devolver el dinero dos veces): cada llamada lleva
	 * una clave fija derivada de la saga, en la cabecera Idempotency-Key:
	 *   - la devolucion, transferencia-N-devolucion. Cuenta guarda la clave en la MISMA transaccion
	 *     local que suma el saldo (tabla operacion_aplicada, con la clave como llave primaria). Si la
	 *     clave ya esta, no suma otra vez y contesta lo mismo que la primera vez. Si dos reintentos
	 *     llegan juntos, la llave primaria deja pasar a uno y la transaccion del otro se deshace.
	 *   - los asientos, transferencia-N-asiento-cargo y -asiento-devolucion. Movimiento guarda la
	 *     clave con el asiento (columna unica) y, si ya existe, regresa el que ya tenia.
	 * Sin la clave, el peor caso es real: la devolucion SI se aplico, la respuesta se perdio (timeout),
	 * el reintento la manda otra vez y origen recibe el dinero dos veces.
	 */
	private Transferencia compensar(Transferencia t, String motivo) {
		try {
			CuentaRemota origen = cuentas.abonar(t.getClaveOrigen(), t.getMonto(), t.clave("devolucion"));
			movimientos.registrar(t.clave("asiento-cargo"), t.getClaveOrigen(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
				t.getMonto().negate(), t.getSaldoOrigenTrasCargo(), "a " + t.getClaveDestino());
			movimientos.registrar(t.clave("asiento-devolucion"), t.getClaveOrigen(), TipoMovimiento.DEVOLUCION_TRANSFERENCIA,
				t.getMonto(), origen.saldo(), "devolucion de la transferencia " + t.getId() + ": no llego a " + t.getClaveDestino());
			t.marcarCompensada(motivo);
		} catch (RuntimeException devolucionFallida) {
			log.error("Transferencia {}: la devolucion tambien fallo ({}); queda COMPENSACION_PENDIENTE",
				t.getId(), devolucionFallida.getMessage());
			t.marcarCompensacionPendiente(motivo);
		}
		return guardar(t);
	}

	/**
	 * Lo llama ReintentoDeCompensaciones cada pocos segundos. Vuelve a intentar la compensacion de
	 * cada transferencia en COMPENSACION_PENDIENTE (con las mismas claves: ver compensar). Regresa
	 * cuantas quedaron COMPENSADA en esta vuelta.
	 */
	public int reintentarCompensacionesPendientes() {
		int terminadas = 0;
		for (Transferencia pendiente : transferencias.findByEstado(COMPENSACION_PENDIENTE)) {
			// motivo null: se conserva el del abono original
			if (compensar(pendiente, null).getEstado() == COMPENSADA) {
				terminadas++;
			}
		}
		return terminadas;
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}

	@Transactional(readOnly = true)
	public List<Transferencia> buscarPorEstado(EstadoTransferencia estado) {
		return transferencias.findByEstado(estado);
	}

	/** Cada paso de la saga se guarda en su propia transaccion (la de save) y deja una linea en el log. */
	private Transferencia guardar(Transferencia t) {
		Transferencia guardada = transferencias.save(t);
		log.info("Transferencia {}: {}", guardada.getId(), guardada.getEstado());
		return guardada;
	}

	/**
	 * Solo para probar la compensacion con todo arriba: si FALLAR_ABONO_A_CLABE trae la CLABE destino,
	 * el abono "falla" aqui mismo, como si cuenta hubiera contestado 503, sin llegar a cuenta (y sin
	 * contar para el circuit breaker). Vacia, que es lo normal, no hace nada.
	 */
	private CuentaRemota abonarADestino(Transferencia t) {
		if (!fallarAbonoAClabe.isEmpty() && fallarAbonoAClabe.equals(t.getClaveDestino())) {
			throw new ErrorRemotoException("cuenta", 503, "Falla provocada con FALLAR_ABONO_A_CLABE=" + fallarAbonoAClabe);
		}
		return cuentas.abonar(t.getClaveDestino(), t.getMonto(), t.clave("abono"));
	}

	/**
	 * Solo para la demo en clase: abre una ventana entre el cargo y el abono para apagar cuenta a
	 * mano. En v06 servia para ver el dinero "en el aire"; en v08, para ver que la saga lo regresa
	 * (o lo deja en COMPENSACION_PENDIENTE si cuenta sigue apagada). Con PAUSA_ENTRE_CARGO_Y_ABONO_MS=0
	 * (el valor normal) no hace nada.
	 */
	private void pausaDeDemo() {
		if (pausaEntreCargoYAbonoMs <= 0) {
			return;
		}
		try {
			Thread.sleep(pausaEntreCargoYAbonoMs);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}
}
