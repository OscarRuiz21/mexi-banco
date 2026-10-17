package mx.mexibanco.mensajeria;

/**
 * v09: el broker no confirmo que tiene el evento (no contesto, dijo que no, o ninguna cola lo
 * recibio). Es una falla de infraestructura, no una regla de negocio: quien publica decide que hacer
 * (ver TransferenciaService.avisar).
 */
public class EventoNoPublicadoException extends RuntimeException {

	public EventoNoPublicadoException(String mensaje) {
		super(mensaje);
	}

	public EventoNoPublicadoException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
