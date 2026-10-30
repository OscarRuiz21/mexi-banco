package mx.mexibanco.cuenta;

import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Reglas de la cuenta. Es la UNICA clase que toca CuentaRepository, y este es el unico servicio
 * que abre la base "cuenta". Transferencia y SPEI mueven saldos por HTTP
 * (POST /cuentas/{clabe}/cargos y /abonos), nunca tocando la tabla.
 */
@Service
public class CuentaService {

	private final CuentaRepository cuentas;
	private final MovimientoCliente movimientos;
	private final OperacionAplicadaRepository operaciones;

	public CuentaService(CuentaRepository cuentas, MovimientoCliente movimientos, OperacionAplicadaRepository operaciones) {
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.operaciones = operaciones;
	}

	/**
	 * Si el servicio movimiento falla, la excepcion sale de aqui y la transaccion LOCAL se deshace:
	 * la cuenta no se crea. Lo contrario no esta cubierto: si movimiento guardo el asiento y luego
	 * falla el commit de aqui, queda un deposito sin cuenta. Ya no hay una transaccion que abarque a los dos.
	 */
	@Transactional
	public Cuenta abrir(String clabe, String titular, BigDecimal saldoInicial) {
		if (cuentas.existsByClabe(clabe)) {
			throw new ConflictoException("Ya existe una cuenta con la CLABE " + clabe);
		}
		Cuenta cuenta = cuentas.save(new Cuenta(clabe, titular, saldoInicial));
		// El saldo es cache de los movimientos: si la cuenta nace con dinero, el ledger lo registra
		if (saldoInicial.signum() > 0) {
			movimientos.registrar(clabe, TipoMovimiento.DEPOSITO, saldoInicial, cuenta.getSaldo(), "saldo inicial");
		}
		return cuenta;
	}

	/**
	 * La lectura que AUTORIZA: la usan cargar y abonar, siempre contra Postgres. v11: no la cachees.
	 * Lo que el cliente ve por GET /cuentas/{clabe} sale de ConsultaDeSaldo, que si puede ir a la cache.
	 */
	@Transactional(readOnly = true)
	public Cuenta consultar(String clabe) {
		return cuentas.findByClabe(clabe)
			.orElseThrow(() -> new NoEncontradoException("No existe la CLABE " + clabe));
	}

	/**
	 * Resta el monto. En el monolito participaba en la transaccion de quien la llamaba; ahora cada
	 * cargo se confirma solo, en cuanto responde. Si despues falla el abono, este cargo ya ocurrio
	 * (y es la saga de transferencia la que lo devuelve).
	 *
	 * v08: clave es la Idempotency-Key (opcional; null = sin proteccion, como antes). Si ya se aplico
	 * un cargo con esa clave, no se vuelve a restar: se contesta lo mismo que la primera vez.
	 */
	// TODO S11: @CacheEvict(cacheNames = ConfiguracionCache.CUENTAS, key = "#clabe"): el saldo cambio, se borra la copia; con transactionAware() el DEL va DESPUES del commit.
	@Transactional
	public SaldoAplicado cargar(String clabe, BigDecimal monto, String clave) {
		Optional<SaldoAplicado> repetida = yaAplicada(clave, clabe, OperacionAplicada.Tipo.CARGO, monto);
		if (repetida.isPresent()) {
			return repetida.get();
		}
		Cuenta cuenta = consultar(clabe);
		if (cuenta.getSaldo().compareTo(monto) < 0) {
			throw new ReglaDeNegocioException("Saldo insuficiente en la CLABE " + clabe);
		}
		cuenta.aplicar(monto.negate());
		return recordar(clave, cuenta, OperacionAplicada.Tipo.CARGO, monto);
	}

	/**
	 * Suma el monto. v08: con la misma Idempotency-Key solo suma la primera vez. Asi es como la
	 * devolucion de la saga (transferencia-N-devolucion) se puede reintentar sin regalar dinero.
	 */
	// TODO S11: el mismo @CacheEvict que en cargar.
	@Transactional
	public SaldoAplicado abonar(String clabe, BigDecimal monto, String clave) {
		Optional<SaldoAplicado> repetida = yaAplicada(clave, clabe, OperacionAplicada.Tipo.ABONO, monto);
		if (repetida.isPresent()) {
			return repetida.get();
		}
		Cuenta cuenta = consultar(clabe);
		cuenta.aplicar(monto);
		return recordar(clave, cuenta, OperacionAplicada.Tipo.ABONO, monto);
	}

	/**
	 * Si la clave ya se uso: para la misma operacion es un reintento (se regresa el saldo guardado);
	 * para otra (otra cuenta, otro monto) es un error de quien llama (409).
	 */
	private Optional<SaldoAplicado> yaAplicada(String clave, String clabe, OperacionAplicada.Tipo tipo, BigDecimal monto) {
		if (clave == null || clave.isBlank()) {
			return Optional.empty();
		}
		return operaciones.findByClave(clave).map(previa -> {
			if (!previa.esLaMisma(clabe, tipo, monto)) {
				throw new ConflictoException("La Idempotency-Key " + clave + " ya se uso para otra operacion");
			}
			return new SaldoAplicado(previa.getClabe(), consultar(clabe).getTitular(), previa.getSaldoResultante());
		});
	}

	/**
	 * Guarda la clave en la misma transaccion que el cambio de saldo. Si otra peticion con la misma
	 * clave gano la carrera, el UNIQUE (o el @Version de Cuenta) hace fallar el commit de esta y su
	 * cambio de saldo se deshace: el dinero se mueve una sola vez.
	 */
	private SaldoAplicado recordar(String clave, Cuenta cuenta, OperacionAplicada.Tipo tipo, BigDecimal monto) {
		if (clave != null && !clave.isBlank()) {
			operaciones.save(new OperacionAplicada(clave, cuenta.getClabe(), tipo, monto, cuenta.getSaldo()));
		}
		return SaldoAplicado.de(cuenta);
	}
}
