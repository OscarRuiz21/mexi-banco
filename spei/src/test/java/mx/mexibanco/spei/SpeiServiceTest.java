package mx.mexibanco.spei;

import mx.mexibanco.compartido.ConflictoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Nueva en v06a: la idempotencia de SPEI sigue siendo local al servicio spei y se prueba sin red. */
@ExtendWith(MockitoExtension.class)
class SpeiServiceTest {

	private static final String CLAVE = "demo-1";
	private static final SpeiService.SolicitudSpei DATOS =
		new SpeiService.SolicitudSpei("002180000000000001", "BANCO-X", "012180000000000009", new BigDecimal("50"));

	@Mock
	SpeiSolicitudRepository solicitudes;

	@Mock
	SpeiTransacciones transacciones;

	@InjectMocks
	SpeiService servicio;

	@Test
	void laPrimeraVezReservaYEjecuta() {
		SpeiSolicitud reservada = new SpeiSolicitud(CLAVE, "002180000000000001", "BANCO-X", "012180000000000009", new BigDecimal("50"));
		when(transacciones.reservar(CLAVE, DATOS)).thenReturn(reservada);
		when(transacciones.ejecutar(reservada)).thenReturn(reservada);

		servicio.procesar(CLAVE, DATOS);

		verify(transacciones).ejecutar(reservada);
	}

	@Test
	void repetirUnaClaveYaEnviadaRegresaLoMismoSinCobrarOtraVez() {
		SpeiSolicitud enviada = new SpeiSolicitud(CLAVE, "002180000000000001", "BANCO-X", "012180000000000009", new BigDecimal("50"));
		enviada.marcarEnviado();
		when(transacciones.reservar(CLAVE, DATOS)).thenThrow(new DataIntegrityViolationException("unique"));
		when(solicitudes.findByIdempotencyKey(CLAVE)).thenReturn(Optional.of(enviada));

		assertThat(servicio.procesar(CLAVE, DATOS)).isSameAs(enviada);
		verify(transacciones, never()).ejecutar(any());
	}

	@Test
	void repetirUnaClaveEnVueloEsConflicto() {
		SpeiSolicitud enVuelo = new SpeiSolicitud(CLAVE, "002180000000000001", "BANCO-X", "012180000000000009", new BigDecimal("50"));
		when(transacciones.reservar(CLAVE, DATOS)).thenThrow(new DataIntegrityViolationException("unique"));
		when(solicitudes.findByIdempotencyKey(CLAVE)).thenReturn(Optional.of(enVuelo));

		assertThatThrownBy(() -> servicio.procesar(CLAVE, DATOS)).isInstanceOf(ConflictoException.class);
		verify(transacciones, never()).ejecutar(any());
	}
}
