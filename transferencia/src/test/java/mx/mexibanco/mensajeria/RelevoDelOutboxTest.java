package mx.mexibanco.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reto Outbox: el relevo toma las filas pendientes, las publica (con confirmacion) y las marca. Si
 * el broker no confirma, la fila se queda pendiente para la siguiente vuelta.
 */
@ExtendWith(MockitoExtension.class)
class RelevoDelOutboxTest {

	private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

	@Mock
	EventoPendienteRepository pendientes;

	@Mock
	PublicadorDeEventos publicador;

	RelevoDelOutbox relevo;

	@BeforeEach
	void armar() {
		relevo = new RelevoDelOutbox(pendientes, publicador, json, 50);
	}

	private EventoTransferencia evento(long transferencia) {
		return EventoTransferencia.realizada(UUID.randomUUID(), transferencia, "002180000000000001", "002180000000000002",
			new BigDecimal("10"), new BigDecimal("90"), new BigDecimal("110"), Instant.parse("2026-10-16T18:00:00Z"));
	}

	private EventoPendiente pendiente(EventoTransferencia evento) throws Exception {
		return new EventoPendiente(evento.eventoId(), evento.routingKey(), json.writeValueAsString(evento));
	}

	@Test
	void publicaLasPendientesEnOrdenYLasMarcaComoPublicadas() throws Exception {
		EventoTransferencia primero = evento(1);
		EventoTransferencia segundo = evento(2);
		EventoPendiente uno = pendiente(primero);
		EventoPendiente dos = pendiente(segundo);
		when(pendientes.tomarPendientes(anyInt())).thenReturn(List.of(uno, dos));

		int publicados = relevo.publicarPendientes();

		assertThat(publicados).isEqualTo(2);
		verify(publicador).publicar(primero);
		verify(publicador).publicar(segundo);
		assertThat(uno.getPublicado()).isNotNull();
		assertThat(dos.getPublicado()).isNotNull();
	}

	/** Broker caido: la fila sigue pendiente (se reintenta en la siguiente vuelta) y no se intenta el resto del lote. */
	@Test
	void siElBrokerNoConfirmaLaFilaSigueSinPublicarYSeCuentaElIntento() throws Exception {
		EventoTransferencia primero = evento(1);
		EventoTransferencia segundo = evento(2);
		EventoPendiente uno = pendiente(primero);
		EventoPendiente dos = pendiente(segundo);
		when(pendientes.tomarPendientes(anyInt())).thenReturn(List.of(uno, dos));
		doThrow(new EventoNoPublicadoException("Connection refused")).when(publicador).publicar(primero);

		int publicados = relevo.publicarPendientes();

		assertThat(publicados).isZero();
		assertThat(uno.getPublicado()).isNull();
		assertThat(uno.getIntentos()).isEqualTo(1);
		verify(publicador, never()).publicar(segundo);
	}

	@Test
	void sinPendientesNoPublicaNada() {
		when(pendientes.tomarPendientes(anyInt())).thenReturn(List.of());

		assertThat(relevo.publicarPendientes()).isZero();
		verify(publicador, never()).publicar(any());
	}
}
