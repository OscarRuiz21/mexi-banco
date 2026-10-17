package mx.mexibanco.notificacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

/**
 * v09: la marca de "este evento ya lo procese". Una fila por eventoId, que es la llave primaria:
 * la base no deja guardar dos filas con el mismo id. Se escribe en la MISMA transaccion que los
 * avisos (ver NotificacionService.avisarDeTransferencia): o quedan la marca y los avisos, o ninguno.
 *
 * Por que hace falta: RabbitMQ entrega "al menos una vez". Si notificacion guarda los avisos y se
 * cae antes del ack, el broker vuelve a entregar el mensaje; y transferencia podria publicar el
 * mismo evento dos veces. Sin la marca, el cliente veria el aviso dos veces.
 *
 * La tabla la crea ddl-auto=update (como el resto de las tablas de este servicio).
 */
@Entity
public class EventoProcesado {

	@Id
	private UUID eventoId;

	@Column(nullable = false, length = 40)
	private String tipo;

	@Column(nullable = false)
	private Long transferenciaId;

	@Column(nullable = false)
	private Instant procesado;

	protected EventoProcesado() {
	}

	public UUID getEventoId() {
		return eventoId;
	}

	public String getTipo() {
		return tipo;
	}

	public Long getTransferenciaId() {
		return transferenciaId;
	}

	public Instant getProcesado() {
		return procesado;
	}
}
