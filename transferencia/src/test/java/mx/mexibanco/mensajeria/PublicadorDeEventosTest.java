package mx.mexibanco.mensajeria;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * v09: lo que hace el publicador con la confirmacion del broker (publisher confirm). RabbitTemplate
 * es un doble que, en lugar de mandar algo, contesta como contestaria RabbitMQ: ack, nack, "no
 * hay cola para esta routing key" o nada. Que el mensaje llegue de verdad se prueba con Compose.
 */
@ExtendWith(MockitoExtension.class)
class PublicadorDeEventosTest {

	private static final EventoTransferencia EVENTO = EventoTransferencia.realizada(
		UUID.fromString("7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10"), 12L, "002180000000000001", "002180000000000002",
		new BigDecimal("200"), new BigDecimal("800"), new BigDecimal("700"), Instant.parse("2026-10-16T18:00:00Z"));

	@Mock
	RabbitTemplate rabbit;

	PublicadorDeEventos publicador;

	@BeforeEach
	void armar() {
		publicador = new PublicadorDeEventos(rabbit, 200);
	}

	/** Lo que haria el broker con este mensaje: se le da la correlacion para que la complete (o no). */
	private void elBroker(Consumer<CorrelationData> contesta) {
		doAnswer(i -> {
			contesta.accept(i.getArgument(4));
			return null;
		}).when(rabbit).convertAndSend(eq("transferencias"), eq("transferencia.realizada"), eq(EVENTO),
			any(MessagePostProcessor.class), any(CorrelationData.class));
	}

	@Test
	void conAckDelBrokerPublicaSinError() {
		elBroker(correlacion -> correlacion.getFuture().complete(new CorrelationData.Confirm(true, null)));

		publicador.publicar(EVENTO);

		verify(rabbit).convertAndSend(eq("transferencias"), eq("transferencia.realizada"), eq(EVENTO),
			any(MessagePostProcessor.class), any(CorrelationData.class));
	}

	/** Persistente (sobrevive a un reinicio del broker si la cola es durable) y con el eventoId como messageId. */
	@Test
	void elMensajeSaleConEntregaPersistenteYConElEventoIdComoMessageId() {
		elBroker(correlacion -> correlacion.getFuture().complete(new CorrelationData.Confirm(true, null)));

		publicador.publicar(EVENTO);

		ArgumentCaptor<MessagePostProcessor> ajuste = ArgumentCaptor.forClass(MessagePostProcessor.class);
		ArgumentCaptor<CorrelationData> correlacion = ArgumentCaptor.forClass(CorrelationData.class);
		verify(rabbit).convertAndSend(eq("transferencias"), eq("transferencia.realizada"), eq(EVENTO), ajuste.capture(), correlacion.capture());
		Message mensaje = ajuste.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
		assertThat(mensaje.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
		assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo(EVENTO.eventoId().toString());
		assertThat(correlacion.getValue().getId()).isEqualTo(EVENTO.eventoId().toString());
	}

	@Test
	void conNackDelBrokerFalla() {
		elBroker(correlacion -> correlacion.getFuture().complete(new CorrelationData.Confirm(false, "disco lleno")));

		assertThatThrownBy(() -> publicador.publicar(EVENTO))
			.isInstanceOf(EventoNoPublicadoException.class)
			.hasMessageContaining("disco lleno");
	}

	/** El broker acepto el mensaje pero ninguna cola lo recibio (con mandatory, lo regresa): se perderia. */
	@Test
	void siNingunaColaLoRecibeFalla() {
		elBroker(correlacion -> {
			correlacion.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "transferencias", "transferencia.realizada"));
			correlacion.getFuture().complete(new CorrelationData.Confirm(true, null));
		});

		assertThatThrownBy(() -> publicador.publicar(EVENTO))
			.isInstanceOf(EventoNoPublicadoException.class)
			.hasMessageContaining("NO_ROUTE");
	}

	@Test
	void siElBrokerNoConfirmaATiempoFalla() {
		elBroker(correlacion -> { /* nunca contesta */ });

		assertThatThrownBy(() -> publicador.publicar(EVENTO))
			.isInstanceOf(EventoNoPublicadoException.class)
			.hasMessageContaining("200 ms");
	}
}
