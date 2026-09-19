package mx.mexibanco.compartido;

/** La operacion choca con algo que ya existe o que ya esta en curso (HTTP 409). */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}
}
