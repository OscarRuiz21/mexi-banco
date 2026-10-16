package mx.mexibanco.movimiento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Nueva en v06a: movimiento ya es su propio servicio y merece su propia prueba. Sin HTTP ni base. */
@ExtendWith(MockitoExtension.class)
class MovimientoServiceTest {

	@Mock
	MovimientoRepository movimientos;

	@InjectMocks
	MovimientoService servicio;

	@Test
	void registrarGuardaElAsientoTalComoSeLoDictan() {
		when(movimientos.save(any(Movimiento.class))).thenAnswer(i -> i.getArgument(0));

		servicio.registrar(null, "002180000000000001", TipoMovimiento.TRANSFERENCIA_ENVIADA,
			new BigDecimal("-200"), new BigDecimal("800"), "a 002180000000000002");

		ArgumentCaptor<Movimiento> guardado = ArgumentCaptor.forClass(Movimiento.class);
		verify(movimientos).save(guardado.capture());
		assertThat(guardado.getValue().getMonto()).isEqualByComparingTo("-200");
		assertThat(guardado.getValue().getSaldoResultante()).isEqualByComparingTo("800");
		assertThat(guardado.getValue().getFecha()).isNotNull();
	}

	/** v08: el reintento de la compensacion vuelve a mandar el asiento con la misma clave; no se duplica. */
	@Test
	void unAsientoQueYaLlegoConEsaClaveNoSeGuardaDosVeces() {
		Movimiento yaGuardado = new Movimiento("transferencia-12-asiento-devolucion", "002180000000000001",
			TipoMovimiento.DEVOLUCION_TRANSFERENCIA, new BigDecimal("200"), new BigDecimal("1000"), "devolucion");
		when(movimientos.findByClave("transferencia-12-asiento-devolucion")).thenReturn(Optional.of(yaGuardado));

		Movimiento respuesta = servicio.registrar("transferencia-12-asiento-devolucion", "002180000000000001",
			TipoMovimiento.DEVOLUCION_TRANSFERENCIA, new BigDecimal("200"), new BigDecimal("1000"), "devolucion");

		assertThat(respuesta).isSameAs(yaGuardado);
		verify(movimientos, never()).save(any());
	}
}
