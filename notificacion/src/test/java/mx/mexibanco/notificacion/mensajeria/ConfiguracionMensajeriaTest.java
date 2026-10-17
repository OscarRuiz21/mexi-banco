package mx.mexibanco.notificacion.mensajeria;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** v09: lo que notificacion le pide declarar al broker, con los nombres y argumentos del contrato del lab. */
class ConfiguracionMensajeriaTest {

	private final List<Declarable> declaraciones = new ConfiguracionMensajeria().topologia().getDeclarables().stream().toList();

	private <T extends Declarable> List<T> deTipo(Class<T> tipo) {
		return declaraciones.stream().filter(tipo::isInstance).map(tipo::cast).toList();
	}

	private Queue cola(String nombre) {
		return deTipo(Queue.class).stream().filter(q -> q.getName().equals(nombre)).findFirst().orElseThrow();
	}

	private Exchange exchange(String nombre) {
		return deTipo(Exchange.class).stream().filter(e -> e.getName().equals(nombre)).findFirst().orElseThrow();
	}

	@Test
	void laColaEsDurableYMandaSusRechazosAlDlx() {
		Queue cola = cola("notificacion.transferencias");

		assertThat(cola.isDurable()).isTrue();
		assertThat(cola.isAutoDelete()).isFalse();
		assertThat(cola.isExclusive()).isFalse();
		assertThat(cola.getArguments()).containsExactlyInAnyOrderEntriesOf(Map.of(
			"x-dead-letter-exchange", "transferencias.dlx",
			"x-dead-letter-routing-key", "notificacion.transferencias"));
	}

	@Test
	void losExchangesSonElTopicDeTransferenciasYElDlxDirect() {
		assertThat(exchange("transferencias").getType()).isEqualTo(ExchangeTypes.TOPIC);
		assertThat(exchange("transferencias").isDurable()).isTrue();
		assertThat(exchange("transferencias.dlx").getType()).isEqualTo(ExchangeTypes.DIRECT);
		assertThat(exchange("transferencias.dlx").isDurable()).isTrue();
		assertThat(cola("notificacion.transferencias.dlq").isDurable()).isTrue();
	}

	@Test
	void losEnlacesSonElPatronTransferenciaYLaRoutingKeyDeLaDlq() {
		assertThat(deTipo(Binding.class))
			.extracting(Binding::getExchange, Binding::getDestination, Binding::getRoutingKey)
			.containsExactlyInAnyOrder(
				tuple("transferencias", "notificacion.transferencias", "transferencia.*"),
				tuple("transferencias.dlx", "notificacion.transferencias.dlq", "notificacion.transferencias"));
	}
}
