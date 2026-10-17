package mx.mexibanco.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Reto Outbox: una fila de la tabla outbox es un evento que transferencia YA decidio publicar y que
 * todavia no sabe si el broker tiene. Se escribe en la misma transaccion que el estado final de la
 * transferencia (ver TransferenciaService) y RelevoDelOutbox la publica despues.
 *
 *  - id: el eventoId. Una fila por evento.
 *  - payload: el evento en JSON, tal cual se publicara.
 *  - publicado: null mientras esta pendiente; la hora en que el broker lo confirmo, despues.
 *  - intentos: cuantas veces el relevo trato y el broker no confirmo.
 *
 * La tabla la crea ddl-auto=update como las demas. En un sistema real llevaria un indice parcial
 * (WHERE publicado IS NULL) y un trabajo que borre lo publicado viejo.
 */
@Entity
@Table(name = "outbox")
public class EventoPendiente {

	@Id
	private UUID id;

	@Column(nullable = false, length = 100)
	private String routingKey;

	@Column(nullable = false, columnDefinition = "text")
	private String payload;

	@Column(nullable = false)
	private Instant creado;

	@Column
	private Instant publicado;

	@Column(nullable = false)
	private int intentos;

	protected EventoPendiente() {
	}

	public EventoPendiente(UUID id, String routingKey, String payload) {
		this.id = id;
		this.routingKey = routingKey;
		this.payload = payload;
		this.creado = Instant.now();
	}

	public void marcarPublicado() {
		this.publicado = Instant.now();
	}

	public void contarIntentoFallido() {
		this.intentos++;
	}

	public UUID getId() {
		return id;
	}

	public String getRoutingKey() {
		return routingKey;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getCreado() {
		return creado;
	}

	public Instant getPublicado() {
		return publicado;
	}

	public int getIntentos() {
		return intentos;
	}
}
