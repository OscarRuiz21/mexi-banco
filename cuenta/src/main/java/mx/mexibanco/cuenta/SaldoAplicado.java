package mx.mexibanco.cuenta;

import java.math.BigDecimal;

/**
 * Lo que contestan POST /cuentas/{clabe}/cargos y /abonos desde v08: la cuenta y su saldo justo
 * despues de ESA operacion. Si es un reintento con la misma Idempotency-Key, el saldo es el que se
 * guardo la primera vez, no el de hoy. Mismo JSON que antes (clabe, titular, saldo), sin el id interno.
 */
public record SaldoAplicado(String clabe, String titular, BigDecimal saldo) {

	static SaldoAplicado de(Cuenta cuenta) {
		return new SaldoAplicado(cuenta.getClabe(), cuenta.getTitular(), cuenta.getSaldo());
	}
}
