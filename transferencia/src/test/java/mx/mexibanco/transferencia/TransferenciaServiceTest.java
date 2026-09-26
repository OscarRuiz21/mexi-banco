package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Las mismas tres pruebas del monolito, ahora con dobles de los CLIENTES HTTP en lugar de los
 * servicios de otros modulos. La cuarta es nueva y documenta lo que se rompio al partir.
 */
@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";
	private static final BigDecimal MONTO = new BigDecimal("200");

	@Mock
	TransferenciaRepository transferencias;

	@Mock
	CuentaCliente cuentas;

	@Mock
	MovimientoCliente movimientos;

	@Mock
	NotificacionCliente notificaciones;

	TransferenciaService servicio;

	@BeforeEach
	void armar() {
		servicio = new TransferenciaService(transferencias, cuentas, movimientos, notificaciones, 0);
	}

	@Test
	void transferirCargaAbonaRegistraDosAsientosYNotificaDos() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> i.getArgument(0));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getMonto()).isEqualByComparingTo(MONTO);
		verify(movimientos).registrar(eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA), eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA), eq(MONTO), eq(new BigDecimal("700")), any());
		verify(notificaciones, times(2)).notificar(any());
	}

	@Test
	void transferirAUnaCuentaQueNoExisteNoCobraNada() {
		when(cuentas.consultar(ORIGEN)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class)
			.extracting("estado").isEqualTo(404);
		verify(cuentas, never()).cargar(any(), any());
		verifyNoInteractions(movimientos, transferencias, notificaciones);
	}

	@Test
	void transferirALaMismaCuentaSeRechaza() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
		verifyNoInteractions(cuentas, movimientos, transferencias, notificaciones);
	}

	/** Lo que se rompio: si el abono falla, nadie devuelve el cargo. En v05.1 esto era un rollback. */
	@Test
	void siElAbonoFallaElCargoYaOcurrioYNadieLoDeshace() {
		when(cuentas.cargar(ORIGEN, MONTO)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
		when(cuentas.abonar(DESTINO, MONTO)).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO)).isInstanceOf(ErrorRemotoException.class);
		verify(cuentas).cargar(ORIGEN, MONTO);
		verify(cuentas, never()).abonar(ORIGEN, MONTO); // nadie le regresa el dinero a origen
		verifyNoInteractions(movimientos, transferencias, notificaciones);
	}
}
