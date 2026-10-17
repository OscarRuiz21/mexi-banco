package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reto Outbox: la bandeja de salida. guardar() NO habla con RabbitMQ; deja el evento en la tabla
 * outbox de la base de transferencia. Lo publica RelevoDelOutbox.
 *
 * MANDATORY: solo se puede llamar dentro de una transaccion ya abierta (la del cambio de estado de
 * la transferencia). Si alguien la llama suelta, truena: un evento guardado fuera de esa transaccion
 * volveria a ser la doble escritura que el Outbox quita.
 */
@Component
public class Outbox {

	private final EventoPendienteRepository pendientes;
	private final ObjectMapper json;

	public Outbox(EventoPendienteRepository pendientes, ObjectMapper json) {
		this.pendientes = pendientes;
		this.json = json;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void guardar(EventoTransferencia evento) {
		try {
			pendientes.save(new EventoPendiente(evento.eventoId(), evento.routingKey(), json.writeValueAsString(evento)));
		} catch (JsonProcessingException imposible) {
			throw new IllegalStateException("No se pudo convertir a JSON el evento " + evento.eventoId(), imposible);
		}
	}
}
