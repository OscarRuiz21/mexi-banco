package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;

import java.util.function.Predicate;

/**
 * Que errores cuentan como falla para el circuit breaker de cuenta (application.yml,
 * record-failure-predicate). Cuentan: no contesto (conexion rechazada, timeout, sin instancias en
 * el directorio) y los 5xx. NO cuentan los 4xx: "no existe la CLABE" o "saldo insuficiente" son
 * respuestas correctas de un cuenta sano, y abrir el circuito por ellas castigaria a todos los
 * demas clientes por el error de uno.
 */
public class FallaDeCuenta implements Predicate<Throwable> {

	@Override
	public boolean test(Throwable error) {
		return !(error instanceof ErrorRemotoException remoto && remoto.getEstado() < 500);
	}
}
