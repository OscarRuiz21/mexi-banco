package mx.mexibanco.transferencia;

import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.cuenta.Cuenta;
import mx.mexibanco.cuenta.CuentaService;
import mx.mexibanco.movimiento.Movimiento;
import mx.mexibanco.movimiento.MovimientoService;
import mx.mexibanco.movimiento.TipoMovimiento;
import mx.mexibanco.notificacion.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Transferencia INTERNA, entre dos cuentas del mismo banco. Todo lo que hace pasa por los servicios
 * de otros modulos (cuenta, movimiento, notificacion) y aun asi es UNA sola transaccion local:
 * cargo, abono, asientos y notificaciones se confirman juntos o ninguno. Eso solo es posible porque
 * hoy todo vive en el mismo proceso y la misma base; al partir el monolito, este metodo es el que
 * deja de poder ser atomico (ver /spei, que ya cruza a otro banco).
 */
@Service
public class TransferenciaService {

	private final TransferenciaRepository transferencias;
	private final CuentaService cuentas;
	private final MovimientoService movimientos;
	private final NotificacionService notificaciones;

	public TransferenciaService(TransferenciaRepository transferencias, CuentaService cuentas,
								MovimientoService movimientos, NotificacionService notificaciones) {
		this.transferencias = transferencias;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
	}

	@Transactional
	public Transferencia transferir(String claveOrigen, String claveDestino, BigDecimal monto) {
		if (claveOrigen.equals(claveDestino)) {
			throw new ReglaDeNegocioException("La cuenta origen y la destino son la misma");
		}
		// Primero que existan las dos, luego el saldo: el mismo orden de errores que antes del refactor
		cuentas.consultar(claveOrigen);
		cuentas.consultar(claveDestino);

		Cuenta origen = cuentas.cargar(claveOrigen, monto);
		Cuenta destino = cuentas.abonar(claveDestino, monto);

		Movimiento cargo = movimientos.registrar(origen.getClabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			monto.negate(), origen.getSaldo(), "a " + destino.getClabe());
		Movimiento abono = movimientos.registrar(destino.getClabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			monto, destino.getSaldo(), "de " + origen.getClabe());

		Transferencia transferencia = transferencias.save(new Transferencia(claveOrigen, claveDestino, monto));

		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);
		return transferencia;
	}

	@Transactional(readOnly = true)
	public Transferencia consultar(Long id) {
		return transferencias.findById(id)
			.orElseThrow(() -> new NoEncontradoException("No existe la transferencia " + id));
	}
}
