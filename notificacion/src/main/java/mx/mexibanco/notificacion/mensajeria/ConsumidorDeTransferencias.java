package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v09: el consumidor de la cola notificacion.transferencias. Por cada evento guarda los avisos y le
 * contesta al broker. Con acknowledge-mode: manual (application.yml) Spring no contesta solo: el
 * mensaje queda "Unacked" hasta que este metodo dice una de tres cosas.
 *
 *  - basicAck: ya lo guarde, borralo. Va DESPUES de guardar (la transaccion ya hizo commit). Si el
 *    proceso muere entre guardar y el ack, el broker no recibio el ack y entrega el mensaje otra vez
 *    (redelivered=true). Eso es "al menos una vez": por eso NotificacionService es idempotente.
 *  - basicNack sin reencolar: este mensaje no sirve (no es JSON, le faltan campos, tipo desconocido).
 *    Reintentarlo no lo arregla, y si se reencolara volveria una y otra vez delante de los buenos. El
 *    broker lo manda al dead letter exchange y termina en notificacion.transferencias.dlq.
 *  - basicNack reencolando: el mensaje esta bien pero fallo algo nuestro (la base no contesto). Vuelve
 *    a la cola para otro intento.
 *
 * El JSON se lee aqui a mano (y no con un MessageConverter) para que un mensaje ilegible sea un caso
 * mas de este metodo, visible, y no una excepcion antes de llegar a el.
 */
@Component
public class ConsumidorDeTransferencias {

	private static final Logger log = LoggerFactory.getLogger(ConsumidorDeTransferencias.class);

	private final NotificacionService notificaciones;
	private final ObjectMapper json;
	private final Validator validador;
	private final long pausaAntesDelAckMs;
	private final long esperaAntesDeReencolarMs;

	public ConsumidorDeTransferencias(NotificacionService notificaciones, ObjectMapper json, Validator validador,
									  @Value("${mexibanco.demo.pausa-antes-del-ack-ms:0}") long pausaAntesDelAckMs,
									  @Value("${mexibanco.mensajeria.espera-antes-de-reencolar-ms:1000}") long esperaAntesDeReencolarMs) {
		this.notificaciones = notificaciones;
		this.json = json;
		this.validador = validador;
		this.pausaAntesDelAckMs = pausaAntesDelAckMs;
		this.esperaAntesDeReencolarMs = esperaAntesDeReencolarMs;
	}

	@RabbitListener(queues = ConfiguracionMensajeria.COLA)
	public void recibir(Message mensaje, Channel canal) throws IOException {
		long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
		boolean reentregado = Boolean.TRUE.equals(mensaje.getMessageProperties().getRedelivered());

		EventoTransferencia evento;
		try {
			evento = leer(mensaje);
		} catch (MensajeInvalido invalido) {
			log.warn("[consumidor] mensaje invalido, va a la DLQ: {}", invalido.getMessage());
			canal.basicNack(etiqueta, false, false);
			return;
		}

		List<Notificacion> avisos;
		try {
			avisos = notificaciones.avisarDeTransferencia(evento);
		} catch (RuntimeException fallaNuestra) {
			// Simplificacion: se reencola tras una espera fija, sin tope. Un servicio real usaria una cola
			// de reintento con TTL o un limite de entregas, para no girar sin fin si la base no vuelve.
			log.error("[consumidor] no se pudo guardar el evento {}; se reencola: {}", evento.eventoId(), fallaNuestra.getMessage());
			pausa(esperaAntesDeReencolarMs);
			canal.basicNack(etiqueta, false, true);
			return;
		}
		log.info("[consumidor] {} de la transferencia {} (evento {}, redelivered={}): {} aviso(s) nuevo(s)",
			evento.tipo(), evento.transferenciaId(), evento.eventoId(), reentregado, avisos.size());

		if (pausaAntesDelAckMs > 0) {
			// Solo para la demo en clase (PAUSA_ANTES_DEL_ACK_MS): los avisos YA estan guardados y el
			// broker todavia no sabe. Mata notificacion en esta ventana y al volver veras redelivered=true.
			log.info("[consumidor] avisos guardados; espero {} ms antes del ack", pausaAntesDelAckMs);
			pausa(pausaAntesDelAckMs);
		}
		canal.basicAck(etiqueta, false);
	}

	/** JSON a EventoTransferencia y validacion. Cualquier problema aqui es del mensaje, no nuestro. */
	private EventoTransferencia leer(Message mensaje) {
		EventoTransferencia evento;
		try {
			evento = json.readValue(mensaje.getBody(), EventoTransferencia.class);
		} catch (IOException noEsElJsonEsperado) {
			String causa = String.valueOf(noEsElJsonEsperado.getMessage()).lines().findFirst().orElse("");
			throw new MensajeInvalido("no se pudo leer el JSON (" + causa + "): " + recorte(mensaje.getBody()));
		}
		if (evento == null) {
			throw new MensajeInvalido("cuerpo vacio");
		}
		Set<ConstraintViolation<EventoTransferencia>> problemas = validador.validate(evento);
		if (!problemas.isEmpty()) {
			throw new MensajeInvalido(problemas.stream()
				.map(p -> p.getPropertyPath() + " " + p.getMessage())
				.sorted()
				.collect(Collectors.joining(", ")) + ": " + recorte(mensaje.getBody()));
		}
		return evento;
	}

	private static String recorte(byte[] cuerpo) {
		String texto = new String(cuerpo, StandardCharsets.UTF_8);
		return texto.length() <= 200 ? texto : texto.substring(0, 200) + "...";
	}

	private static void pausa(long ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}

	/** El mensaje no se puede procesar por como viene. */
	private static class MensajeInvalido extends RuntimeException {
		MensajeInvalido(String detalle) {
			super(detalle);
		}
	}
}
