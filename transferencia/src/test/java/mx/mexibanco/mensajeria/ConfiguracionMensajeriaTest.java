package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v09: el contrato del evento tal como sale al cable. @JsonTest da el ObjectMapper de Spring Boot,
 * el mismo que usa el convertidor en el servicio (por eso la fecha sale como texto ISO-8601).
 */
@JsonTest
class ConfiguracionMensajeriaTest {

	@Autowired
	ObjectMapper json;

	private final ConfiguracionMensajeria configuracion = new ConfiguracionMensajeria();

	@Test
	void elEventoViajaComoJsonConLosNombresDelContratoYLaFechaIso() throws Exception {
		MessageConverter convertidor = configuracion.convertidorJson(json);
		EventoTransferencia evento = EventoTransferencia.revertida(UUID.fromString("7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10"), 12L,
			"002180000000000001", "002180000000000002", new BigDecimal("150"), new BigDecimal("800.00"),
			Instant.parse("2026-10-16T18:00:00.123Z"));

		Message mensaje = convertidor.toMessage(evento, new MessageProperties());

		assertThat(mensaje.getMessageProperties().getContentType()).isEqualTo("application/json");
		JsonNode cuerpo = json.readTree(mensaje.getBody());
		assertThat(cuerpo.get("eventoId").asText()).isEqualTo("7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10");
		assertThat(cuerpo.get("tipo").asText()).isEqualTo("TransferenciaRevertida");
		assertThat(cuerpo.get("transferenciaId").asLong()).isEqualTo(12L);
		assertThat(cuerpo.get("clabeOrigen").asText()).isEqualTo("002180000000000001");
		assertThat(cuerpo.get("clabeDestino").asText()).isEqualTo("002180000000000002");
		assertThat(cuerpo.get("monto").decimalValue()).isEqualByComparingTo("150");
		assertThat(cuerpo.get("saldoOrigen").decimalValue()).isEqualByComparingTo("800.00");
		assertThat(cuerpo.get("saldoDestino").isNull()).isTrue();
		assertThat(cuerpo.get("fecha").asText()).isEqualTo("2026-10-16T18:00:00.123Z");
	}

	@Test
	void elExchangeEsTopicDurableYSeLlamaTransferencias() {
		TopicExchange exchange = configuracion.exchangeTransferencias();

		assertThat(exchange.getName()).isEqualTo("transferencias");
		assertThat(exchange.getType()).isEqualTo(ExchangeTypes.TOPIC);
		assertThat(exchange.isDurable()).isTrue();
		assertThat(exchange.isAutoDelete()).isFalse();
	}
}
