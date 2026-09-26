package mx.mexibanco.compartido;

/**
 * Lo que se busco no existe. Los servicios lanzan excepciones de negocio, nunca codigos HTTP:
 * traducirlas a 404/409/422 es trabajo de la capa web (ManejadorDeErrores).
 */
public class NoEncontradoException extends RuntimeException {

	public NoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
