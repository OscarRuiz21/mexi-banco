package mx.mexibanco.compartido;

/**
 * Otro servicio respondio con un error (4xx o 5xx). Se guarda su codigo tal cual para devolverlo
 * igual al cliente: si cuenta dijo 404, este servicio tambien dice 404, no un 500.
 * Esta clase existe copiada en cada servicio a proposito (ver README, "Sin modulo compartido").
 */
public class ErrorRemotoException extends RuntimeException {

	private final int estado;
	private final String servicio;

	public ErrorRemotoException(String servicio, int estado, String detalle) {
		super(detalle);
		this.servicio = servicio;
		this.estado = estado;
	}

	public int getEstado() {
		return estado;
	}

	public String getServicio() {
		return servicio;
	}
}
