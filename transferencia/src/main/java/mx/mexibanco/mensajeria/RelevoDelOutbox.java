package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reto Outbox: el relevo. Cada OUTBOX_RELEVO_MS (1 s por omision) toma las filas pendientes de la
 * tabla outbox, publica cada evento con PublicadorDeEventos (que espera la confirmacion del broker)
 * y la marca como publicada. Todo en una transaccion: las filas quedan bloqueadas mientras tanto.
 *
 * Si el broker esta caido, la primera publicacion falla, la fila sigue pendiente (con un intento mas)
 * y no se intenta el resto del lote: la siguiente vuelta lo vuelve a intentar. Cuando el broker
 * regresa, los eventos salen solos, en el orden en que se guardaron.
 *
 * AL MENOS UNA VEZ, no exactamente una vez: si el proceso se cae despues de que el broker confirmo y
 * antes del commit que marca la fila, la fila sigue pendiente y el evento se publica OTRA VEZ, con el
 * mismo eventoId. Por eso el consumidor (notificacion) es idempotente. El Outbox garantiza que el
 * evento no se pierde; la idempotencia del consumidor, que no se duplique el efecto.
 *
 * Simplificaciones a proposito: espera fija entre vueltas, sin tope de intentos y sin borrar lo ya
 * publicado. Llamar al broker dentro de una transaccion de base la alarga; aqui es un lote chico y
 * la confirmacion tiene timeout (mexibanco.mensajeria.espera-confirmacion-ms).
 */
@Component
public class RelevoDelOutbox {

	private static final Logger log = LoggerFactory.getLogger(RelevoDelOutbox.class);

	private final EventoPendienteRepository pendientes;
	private final PublicadorDeEventos publicador;
	private final ObjectMapper json;
	private final int lote;

	public RelevoDelOutbox(EventoPendienteRepository pendientes, PublicadorDeEventos publicador, ObjectMapper json,
						   @Value("${mexibanco.outbox.lote:50}") int lote) {
		this.pendientes = pendientes;
		this.publicador = publicador;
		this.json = json;
		this.lote = lote;
	}

	@Scheduled(fixedDelayString = "${mexibanco.outbox.relevo-ms:1000}")
	@Transactional
	public int publicarPendientes() {
		List<EventoPendiente> filas = pendientes.tomarPendientes(lote);
		int publicados = 0;
		for (EventoPendiente fila : filas) {
			try {
				publicador.publicar(leer(fila));
			} catch (EventoNoPublicadoException brokerNoConfirmo) {
				fila.contarIntentoFallido();
				log.warn("Outbox: el evento {} sigue pendiente (intento {}): {}", fila.getId(), fila.getIntentos(),
					brokerNoConfirmo.getMessage());
				break;
			}
			fila.marcarPublicado(); // se escribe al hacer commit de esta transaccion
			publicados++;
		}
		if (publicados > 0) {
			log.info("Outbox: {} evento(s) publicado(s)", publicados);
		}
		return publicados;
	}

	private EventoTransferencia leer(EventoPendiente fila) {
		try {
			return json.readValue(fila.getPayload(), EventoTransferencia.class);
		} catch (JsonProcessingException imposible) {
			throw new IllegalStateException("La fila " + fila.getId() + " del outbox no tiene un evento valido", imposible);
		}
	}
}
