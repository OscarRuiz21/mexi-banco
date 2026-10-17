package mx.mexibanco.notificacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nueva en v06a: el mensaje del aviso se arma igual que en el monolito, ahora desde un DTO.
 * v09: tambien los avisos que llegan como evento de RabbitMQ, y que un evento repetido no avise dos veces.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	private static final String ANA = "002180000000000001";
	private static final String BETO = "002180000000000002";
	private static final UUID EVENTO_ID = UUID.fromString("7d0c3c4e-5b7a-4a43-9a52-0f5f5d1f2a10");
	private static final Instant FECHA = Instant.parse("2026-10-16T18:00:00Z");

	@Mock
	NotificacionRepository notificaciones;

	@Mock
	EventoProcesadoRepository procesados;

	@InjectMocks
	NotificacionService servicio;

	@Test
	void elAvisoDiceTipoMontoSinSignoYSaldo() {
		when(notificaciones.save(any(Notificacion.class))).thenAnswer(i -> i.getArgument(0));

		Notificacion aviso = servicio.notificar(new AvisoDeMovimiento("002180000000000001",
			"TRANSFERENCIA_ENVIADA", new BigDecimal("-200"), new BigDecimal("800")));

		assertThat(aviso.getClabeCuenta()).isEqualTo("002180000000000001");
		assertThat(aviso.getMensaje()).isEqualTo("TRANSFERENCIA_ENVIADA por 200, saldo resultante 800");
	}

	private void marcaNueva() {
		when(procesados.marcarSiEsNuevo(any(), any(), any())).thenReturn(1);
		when(notificaciones.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
	}

	/** Una transferencia realizada son dos avisos: a quien envio y a quien recibio, cada uno con su saldo. */
	@Test
	void unaTransferenciaRealizadaAvisaAOrigenYADestino() {
		marcaNueva();

		List<Notificacion> avisos = servicio.avisarDeTransferencia(new EventoTransferencia(EVENTO_ID, "TransferenciaRealizada",
			12L, ANA, BETO, new BigDecimal("200"), new BigDecimal("800.00"), new BigDecimal("700.00"), FECHA));

		assertThat(avisos).extracting(Notificacion::getClabeCuenta, Notificacion::getMensaje).containsExactly(
			tuple(ANA, "TRANSFERENCIA_ENVIADA por 200, saldo resultante 800.00"),
			tuple(BETO, "TRANSFERENCIA_RECIBIDA por 200, saldo resultante 700.00"));
		verify(procesados).marcarSiEsNuevo(EVENTO_ID, "TransferenciaRealizada", 12L);
	}

	/** Una revertida es un aviso, solo a origen: su dinero regreso. A destino nunca le llego nada. */
	@Test
	void unaTransferenciaRevertidaAvisaSoloAOrigen() {
		marcaNueva();

		List<Notificacion> avisos = servicio.avisarDeTransferencia(new EventoTransferencia(EVENTO_ID, "TransferenciaRevertida",
			13L, ANA, BETO, new BigDecimal("150"), new BigDecimal("800.00"), null, FECHA));

		assertThat(avisos).extracting(Notificacion::getClabeCuenta, Notificacion::getMensaje).containsExactly(
			tuple(ANA, "DEVOLUCION_TRANSFERENCIA por 150, saldo resultante 800.00"));
	}

	/** El mismo eventoId otra vez (reentrega o publicacion repetida): la marca ya existe y no se avisa de nuevo. */
	@Test
	void unEventoRepetidoNoVuelveAAvisar() {
		when(procesados.marcarSiEsNuevo(any(), any(), any())).thenReturn(0);

		List<Notificacion> avisos = servicio.avisarDeTransferencia(new EventoTransferencia(EVENTO_ID, "TransferenciaRealizada",
			12L, ANA, BETO, new BigDecimal("200"), new BigDecimal("800.00"), new BigDecimal("700.00"), FECHA));

		assertThat(avisos).isEmpty();
		verify(notificaciones, never()).saveAll(anyList());
	}
}
