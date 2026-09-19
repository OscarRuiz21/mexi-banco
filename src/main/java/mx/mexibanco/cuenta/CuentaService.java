package mx.mexibanco.cuenta;

import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.movimiento.MovimientoService;
import mx.mexibanco.movimiento.TipoMovimiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Reglas de la cuenta. Es la UNICA clase que toca CuentaRepository: transferencia y SPEI mueven
 * saldos llamando a cargar/abonar, nunca al repositorio. Esa frontera es la que se vuelve una
 * llamada por red cuando cuentas sea su propio servicio.
 */
@Service
public class CuentaService {

	private final CuentaRepository cuentas;
	private final MovimientoService movimientos;

	public CuentaService(CuentaRepository cuentas, MovimientoService movimientos) {
		this.cuentas = cuentas;
		this.movimientos = movimientos;
	}

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

	@Transactional(readOnly = true)
	public Cuenta consultar(String clabe) {
		return cuentas.findByClabe(clabe)
			.orElseThrow(() -> new NoEncontradoException("No existe la CLABE " + clabe));
	}

	/** Resta el monto. Participa en la transaccion de quien la llama: si esa falla, el cargo se deshace. */
	@Transactional
	public Cuenta cargar(String clabe, BigDecimal monto) {
		Cuenta cuenta = consultar(clabe);
		if (cuenta.getSaldo().compareTo(monto) < 0) {
			throw new ReglaDeNegocioException("Saldo insuficiente en la CLABE " + clabe);
		}
		cuenta.aplicar(monto.negate());
		return cuenta;
	}

	@Transactional
	public Cuenta abonar(String clabe, BigDecimal monto) {
		Cuenta cuenta = consultar(clabe);
		cuenta.aplicar(monto);
		return cuenta;
	}
}
