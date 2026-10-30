package mx.mexibanco.cuenta;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * v11: lo que contesta GET /cuentas/{clabe}. Es un dato DERIVADO para mostrar, no la verdad: la
 * verdad es la fila de Postgres que lee CuentaService al autorizar un cargo.
 *
 * leidaEn dice cuando se leyo de Postgres. Si la respuesta sale de la cache, leidaEn es viejo: asi se
 * ve la edad del dato sin abrir Redis ("tu saldo a las 10:42:07"). Es un record de cuatro campos
 * para que en Redis se guarde como un JSON chico y legible.
 */
public record CuentaVista(String clabe, String titular, BigDecimal saldo, Instant leidaEn) {

	static CuentaVista de(Cuenta cuenta) {
		return new CuentaVista(cuenta.getClabe(), cuenta.getTitular(), cuenta.getSaldo(), Instant.now());
	}
}
