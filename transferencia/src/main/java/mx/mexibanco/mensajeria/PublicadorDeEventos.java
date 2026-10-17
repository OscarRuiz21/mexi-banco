package mx.mexibanco.mensajeria;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static mx.mexibanco.mensajeria.ConfiguracionMensajeria.EXCHANGE;

/**
 * v09: publica un EventoTransferencia en el exchange "transferencias" y ESPERA a que RabbitMQ
 * confirme que lo tiene (publisher confirm). Sin esa espera, convertAndSend regresa en cuanto los
 * bytes salen por el socket: si el broker se cae en ese instante, el evento se pierde y nadie se
 * entera.
 *
 * Tres cosas hacen que el mensaje no se pierda dentro del broker:
 *  1. Confirmacion (spring.rabbitmq.publisher-confirm-type: correlated). El broker contesta ack
 *     cuando el mensaje ya quedo en todas las colas que le tocan (y en disco, si es persistente
 *     y la cola es durable). Contesta nack si no pudo guardarlo.
 *  2. mandatory (spring.rabbitmq.template.mandatory: true). Si ninguna cola esta enlazada para esa
 *     routing key (por ejemplo, notificacion nunca ha arrancado y su cola no existe), el broker
 *     descarta el mensaje... y aun asi contesta ack. Con mandatory, antes del ack lo REGRESA y aqui
 *     se trata como falla.
 *  3. Entrega persistente (deliveryMode 2): el mensaje se escribe a disco y sobrevive a un reinicio
 *     del broker si la cola es durable.
 *
 * Esperar la confirmacion de cada mensaje es lento comparado con mandar muchos y confirmar en lote;
 * para una transferencia a la vez es lo mas claro.
 */
@Component
public class PublicadorDeEventos {

	private final RabbitTemplate rabbit;
	private final long esperaConfirmacionMs;

	public PublicadorDeEventos(RabbitTemplate rabbit,
							   @Value("${mexibanco.mensajeria.espera-confirmacion-ms:5000}") long esperaConfirmacionMs) {
		this.rabbit = rabbit;
		this.esperaConfirmacionMs = esperaConfirmacionMs;
	}

	/**
	 * Regresa solo si el broker confirmo el evento. Si no (no hay conexion, nack, nadie lo recibio o no
	 * contesto a tiempo) lanza EventoNoPublicadoException.
	 */
	public void publicar(EventoTransferencia evento) {
		String id = evento.eventoId().toString();
		// La correlacion une la confirmacion que llegara despues (por otro hilo) con ESTE mensaje.
		CorrelationData correlacion = new CorrelationData(id);
		try {
			rabbit.convertAndSend(EXCHANGE, evento.routingKey(), evento, mensaje -> {
				mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
				// El id del evento tambien va como messageId: en el panel de RabbitMQ se ve sin abrir el JSON.
				mensaje.getMessageProperties().setMessageId(id);
				return mensaje;
			}, correlacion);
		} catch (RuntimeException sinConexion) {
			throw new EventoNoPublicadoException("No se pudo mandar el evento " + id + " al broker: " + sinConexion.getMessage(), sinConexion);
		}
		CorrelationData.Confirm confirmacion = esperarConfirmacion(correlacion, id);
		if (!confirmacion.isAck()) {
			throw new EventoNoPublicadoException("El broker rechazo el evento " + id + " (nack): " + confirmacion.getReason());
		}
		ReturnedMessage regresado = correlacion.getReturned();
		if (regresado != null) {
			throw new EventoNoPublicadoException("Ninguna cola recibio el evento " + id + " con la routing key "
				+ regresado.getRoutingKey() + " (" + regresado.getReplyText() + ")");
		}
	}

	private CorrelationData.Confirm esperarConfirmacion(CorrelationData correlacion, String id) {
		try {
			return correlacion.getFuture().get(esperaConfirmacionMs, TimeUnit.MILLISECONDS);
		} catch (TimeoutException sinRespuesta) {
			throw new EventoNoPublicadoException("El broker no confirmo el evento " + id + " en " + esperaConfirmacionMs + " ms");
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
			throw new EventoNoPublicadoException("Se interrumpio la espera de la confirmacion del evento " + id, interrumpido);
		} catch (ExecutionException fallo) {
			throw new EventoNoPublicadoException("Fallo la confirmacion del evento " + id + ": " + fallo.getCause(), fallo);
		}
	}
}
