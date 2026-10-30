package mx.mexibanco.notificacion.mensajeria;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import mx.mexibanco.notificacion.EventoTransferencia;
import mx.mexibanco.notificacion.Notificacion;
import mx.mexibanco.notificacion.NotificacionService;
import mx.mexibanco.notificacion.avisos.BitacoraDeAvisos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v09: que hace el consumidor con cada mensaje y CUANDO le contesta al broker. El canal es un doble:
 * lo que importa es si dijo ack, nack sin reencolar (a la DLQ) o nack reencolando, y en que orden
 * respecto a guardar. El ObjectMapper y el validador son los reales.
 */
@ExtendWith(MockitoExtension.class)
class ConsumidorDeTransferenciasTest {

	private static final long ETIQUETA = 7L;
	private static final String REALIZADA = """
		{"eventoId":"7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10","tipo":"TransferenciaRealizada","transferenciaId":12,
		 "clabeOrigen":"002180000000000001","clabeDestino":"002180000000000002","monto":200,
		 "saldoOrigen":800.00,"saldoDestino":700.00,"fecha":"2026-10-16T18:00:00.123Z"}""";

	@Mock
	NotificacionService notificaciones;

	@Mock
	BitacoraDeAvisos bitacora;

	@Mock
	Channel canal;

	ConsumidorDeTransferencias consumidor;

	@BeforeEach
	void armar() {
		ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
		Validator validador = Validation.buildDefaultValidatorFactory().getValidator();
		consumidor = new ConsumidorDeTransferencias(notificaciones, bitacora, json, validador, 0, 0);
	}

	private static Message mensaje(String cuerpo) {
		MessageProperties propiedades = new MessageProperties();
		propiedades.setDeliveryTag(ETIQUETA);
		propiedades.setContentType("application/json");
		return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
	}

	private void seGuarda() {
		when(notificaciones.avisarDeTransferencia(any())).thenReturn(List.of(new Notificacion("002180000000000001", "x")));
	}

	/** El ack va DESPUES de guardar: si el proceso se cae antes, el broker lo vuelve a entregar. */
	@Test
	void unEventoValidoSeGuardaYDespuesSeConfirma() throws Exception {
		seGuarda();

		consumidor.recibir(mensaje(REALIZADA), canal);

		InOrder enOrden = inOrder(notificaciones, canal);
		enOrden.verify(notificaciones).avisarDeTransferencia(any());
		enOrden.verify(canal).basicAck(ETIQUETA, false);
		verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
	}

	@Test
	void elJsonSeLeeConLosCamposDelContrato() throws Exception {
		seGuarda();

		consumidor.recibir(mensaje(REALIZADA), canal);

		ArgumentCaptor<EventoTransferencia> evento = ArgumentCaptor.forClass(EventoTransferencia.class);
		verify(notificaciones).avisarDeTransferencia(evento.capture());
		assertThat(evento.getValue()).isEqualTo(new EventoTransferencia(UUID.fromString("7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10"),
			"TransferenciaRealizada", 12L, "002180000000000001", "002180000000000002", new BigDecimal("200"),
			new BigDecimal("800.00"), new BigDecimal("700.00"), Instant.parse("2026-10-16T18:00:00.123Z")));
	}

	/** Un repetido no guarda nada nuevo, pero tambien se confirma: si no, el broker lo entregaria para siempre. */
	@Test
	void unEventoRepetidoTambienSeConfirma() throws Exception {
		when(notificaciones.avisarDeTransferencia(any())).thenReturn(List.of());

		consumidor.recibir(mensaje(REALIZADA), canal);

		verify(canal).basicAck(ETIQUETA, false);
	}

	/** Un productor nuevo puede agregar campos: el consumidor ignora los que no conoce (evolucion compatible). */
	@Test
	void losCamposQueNoConoceSeIgnoran() throws Exception {
		seGuarda();

		consumidor.recibir(mensaje(REALIZADA.replace("\"monto\":200", "\"monto\":200,\"canal\":\"app\"")), canal);

		verify(canal).basicAck(ETIQUETA, false);
	}

	/** El mensaje venenoso: reintentarlo no lo arregla. Nack SIN reencolar y el broker lo manda a la DLQ. */
	@Test
	void unJsonMalFormadoVaALaDlqSinTocarLaBase() throws Exception {
		consumidor.recibir(mensaje("{\"eventoId\": esto no es json"), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verify(canal, never()).basicAck(anyLong(), anyBoolean());
		verifyNoInteractions(notificaciones);
	}

	@Test
	void unEventoSinEventoIdVaALaDlq() throws Exception {
		consumidor.recibir(mensaje(REALIZADA.replace("\"eventoId\":\"7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10\",", "")), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verifyNoInteractions(notificaciones);
	}

	@Test
	void unTipoQueNoConoceVaALaDlq() throws Exception {
		consumidor.recibir(mensaje(REALIZADA.replace("TransferenciaRealizada", "TransferenciaInventada")), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verifyNoInteractions(notificaciones);
	}

	@Test
	void unaRealizadaSinSaldoDestinoVaALaDlq() throws Exception {
		consumidor.recibir(mensaje(REALIZADA.replace("\"saldoDestino\":700.00", "\"saldoDestino\":null")), canal);

		verify(canal).basicNack(ETIQUETA, false, false);
		verifyNoInteractions(notificaciones);
	}

	/** Si la base falla, el mensaje no tiene la culpa: nack REENCOLANDO para intentarlo otra vez. */
	@Test
	void siGuardarFallaSeReencolaYNoSeConfirma() throws Exception {
		when(notificaciones.avisarDeTransferencia(any())).thenThrow(new IllegalStateException("la base no contesta"));

		consumidor.recibir(mensaje(REALIZADA), canal);

		verify(canal).basicNack(ETIQUETA, false, true);
		verify(canal, never()).basicAck(anyLong(), anyBoolean());
	}
}
