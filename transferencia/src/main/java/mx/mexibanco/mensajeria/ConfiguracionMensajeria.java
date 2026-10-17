package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * v09: lo que transferencia necesita del broker. Solo el exchange: transferencia PUBLICA en el
 * exchange "transferencias" y no sabe que colas hay detras. Las colas son de quien consume (la de
 * notificacion la declara notificacion).
 *
 * Declarar es idempotente: Spring (RabbitAdmin) manda la declaracion al conectarse y, si el exchange
 * ya existe con las mismas propiedades, no pasa nada. Notificacion declara el mismo exchange, asi que
 * no importa quien arranque primero. Si las propiedades no coincidieran (por ejemplo, uno lo pide
 * durable y otro no), el broker rechaza la declaracion con PRECONDITION_FAILED.
 */
@Configuration
public class ConfiguracionMensajeria {

	public static final String EXCHANGE = "transferencias";

	/** Topic: el consumidor elige que routing keys recibe con patrones (transferencia.*). Durable: sobrevive a un reinicio del broker. */
	@Bean
	TopicExchange exchangeTransferencias() {
		return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
	}

	/**
	 * Los eventos viajan como JSON. Se usa el ObjectMapper de Spring Boot (el mismo de las respuestas
	 * HTTP), que escribe las fechas como texto ISO-8601 y no como numero. Spring Boot le pone este
	 * convertidor al RabbitTemplate por ser el unico MessageConverter del contexto.
	 *
	 * Ojo: ademas del JSON, este convertidor agrega la cabecera __TypeId__ con el nombre de la clase
	 * Java (mx.mexibanco.mensajeria.EventoTransferencia). Notificacion la ignora y lee solo el JSON:
	 * el contrato son los campos, no una clase de este servicio.
	 */
	@Bean
	MessageConverter convertidorJson(ObjectMapper json) {
		return new Jackson2JsonMessageConverter(json);
	}
}
