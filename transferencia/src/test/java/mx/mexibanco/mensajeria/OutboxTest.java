package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Reto Outbox: guardar un evento es una fila con su routing key y su JSON, pendiente de publicar. */
@ExtendWith(MockitoExtension.class)
class OutboxTest {

	private static final EventoTransferencia EVENTO = EventoTransferencia.realizada(
		UUID.fromString("7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10"), 12L, "002180000000000001", "002180000000000002",
		new BigDecimal("200"), new BigDecimal("800"), new BigDecimal("700"), Instant.parse("2026-10-16T18:00:00Z"));

	private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

	@Mock
	EventoPendienteRepository pendientes;

	@Test
	void guardarDejaUnaFilaPendienteConElMismoIdQueElEvento() throws Exception {
		Outbox outbox = new Outbox(pendientes, json);

		outbox.guardar(EVENTO);

		ArgumentCaptor<EventoPendiente> fila = ArgumentCaptor.forClass(EventoPendiente.class);
		verify(pendientes).save(fila.capture());
		assertThat(fila.getValue().getId()).isEqualTo(EVENTO.eventoId());
		assertThat(fila.getValue().getRoutingKey()).isEqualTo("transferencia.realizada");
		assertThat(fila.getValue().getPublicado()).isNull();
		assertThat(json.readValue(fila.getValue().getPayload(), EventoTransferencia.class)).isEqualTo(EVENTO);
	}
}
