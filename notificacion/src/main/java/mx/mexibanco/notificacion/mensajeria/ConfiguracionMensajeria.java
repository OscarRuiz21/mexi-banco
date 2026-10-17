package mx.mexibanco.notificacion.mensajeria;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * v09: lo que notificacion le pide al broker que exista. Spring (RabbitAdmin) lo declara al conectarse;
 * declarar algo que ya existe con las mismas propiedades no hace nada.
 *
 * <pre>
 *  transferencia --transferencia.realizada / .revertida--> [transferencias] (topic)
 *                                                            | transferencia.*
 *                                                            v
 *                                          notificacion.transferencias  --> ConsumidorDeTransferencias
 *                                                            | nack sin reencolar
 *                                                            v
 *                     [transferencias.dlx] (direct) --notificacion.transferencias--> notificacion.transferencias.dlq
 * </pre>
 *
 * La cola es de notificacion: se llama como su duenio y vive aunque notificacion este apagada. Por eso
 * los eventos esperan ahi (Ready en el panel) mientras notificacion no esta.
 */
@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";
	public static final String COLA = "notificacion.transferencias";
	public static final String PATRON = "transferencia.*";
	public static final String DLX = "transferencias.dlx";
	public static final String DLQ = "notificacion.transferencias.dlq";

	@Bean
	Declarables topologia() {
		// El mismo exchange que declara transferencia, con las mismas propiedades (topic, durable).
		// Se declara aqui tambien para poder enlazar la cola aunque notificacion arranque primero.
		TopicExchange transferencias = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();

		// Durable: la cola (y sus mensajes persistentes) sobrevive a un reinicio del broker.
		// Los dos argumentos x-dead-letter-* dicen a donde se va un mensaje RECHAZADO sin reencolar
		// (basicNack con requeue=false): al exchange transferencias.dlx con la routing key
		// notificacion.transferencias. Es el broker quien lo mueve, no este servicio.
		Queue cola = QueueBuilder.durable(COLA)
			.deadLetterExchange(DLX)
			.deadLetterRoutingKey(COLA)
			.build();

		// La DLQ es una cola normal, durable, que nadie consume: ahi se quedan los mensajes venenosos
		// para que una persona los revise (en el panel, Get messages) y decida que hacer.
		DirectExchange dlx = ExchangeBuilder.directExchange(DLX).durable(true).build();
		Queue dlq = QueueBuilder.durable(DLQ).build();

		return new Declarables(
			transferencias, cola, BindingBuilder.bind(cola).to(transferencias).with(PATRON),
			dlx, dlq, BindingBuilder.bind(dlq).to(dlx).with(COLA));
	}
}
