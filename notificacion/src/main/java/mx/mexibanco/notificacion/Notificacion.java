package mx.mexibanco.notificacion;

import jakarta.persistence.*;

import java.time.Instant;

/** Lo que se le aviso al cliente. Hoy se guarda en su propia tabla; mas adelante saldra por un broker. */
@Entity
public class Notificacion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 18)
	private String clabeCuenta;

	@Column(nullable = false)
	private String mensaje;

	@Column(nullable = false)
	private Instant fecha;

	protected Notificacion() {
	}

	public Notificacion(String clabeCuenta, String mensaje) {
		this.clabeCuenta = clabeCuenta;
		this.mensaje = mensaje;
		this.fecha = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getClabeCuenta() {
		return clabeCuenta;
	}

	public String getMensaje() {
		return mensaje;
	}

	public Instant getFecha() {
		return fecha;
	}
}
