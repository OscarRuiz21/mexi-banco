package mx.mexibanco.compartido;

/** La peticion esta bien formada pero una regla del banco la impide, p. ej. saldo insuficiente (HTTP 422). */
public class ReglaDeNegocioException extends RuntimeException {

	public ReglaDeNegocioException(String mensaje) {
		super(mensaje);
	}
}
