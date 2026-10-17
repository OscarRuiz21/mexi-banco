package mx.mexibanco.transferencia;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.TipoMovimiento;
import mx.mexibanco.compartido.ErrorRemotoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.mensajeria.EventoNoPublicadoException;
import mx.mexibanco.mensajeria.EventoTransferencia;
import mx.mexibanco.mensajeria.PublicadorDeEventos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static mx.mexibanco.transferencia.EstadoTransferencia.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * v08: la transferencia es una saga orquestada. Las pruebas usan dobles de los CLIENTES HTTP y del
 * repositorio; el repositorio falso anota el estado con el que se guardo la fila en cada paso, que
 * es justo lo que se quiere comprobar: si el servicio se cae a la mitad, la base dice donde quedo.
 *
 * v09: ya no se llama a notificacion por HTTP. Al terminar, la saga PUBLICA un evento: el doble es el
 * publicador (la frontera con RabbitMQ) y lo que se revisa es que evento sale, con que datos y cuando.
 */
@ExtendWith(MockitoExtension.class)
class TransferenciaServiceTest {

	private static final String ORIGEN = "002180000000000001";
	private static final String DESTINO = "002180000000000002";
	private static final BigDecimal MONTO = new BigDecimal("200");
	private static final long ID = 12L;

	@Mock
	TransferenciaRepository transferencias;

	@Mock
	CuentaCliente cuentas;

	@Mock
	MovimientoCliente movimientos;

	@Mock
	PublicadorDeEventos eventos;

	TransferenciaService servicio;

	/** El estado de la fila en cada save(), en orden. */
	List<EstadoTransferencia> estadosGuardados = new ArrayList<>();

	@BeforeEach
	void armar() {
		servicio = nuevoServicio("");
	}

	private TransferenciaService nuevoServicio(String fallarAbonoAClabe) {
		return new TransferenciaService(transferencias, cuentas, movimientos, eventos, 0, fallarAbonoAClabe);
	}

	/** Como la base: al primer save le pone id, y en cada save anota el estado con el que llego. */
	private void repositorioQueAnota() {
		when(transferencias.save(any(Transferencia.class))).thenAnswer(i -> {
			Transferencia t = i.getArgument(0);
			if (t.getId() == null) {
				ReflectionTestUtils.setField(t, "id", ID);
			}
			estadosGuardados.add(t.getEstado());
			return t;
		});
	}

	private void cargoExitoso() {
		when(cuentas.cargar(ORIGEN, MONTO, "transferencia-12-cargo")).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("800")));
	}

	private void ledgerQueAcepta() {
		when(movimientos.registrar(anyString(), any(), any(), any(), any(), any())).thenReturn(mock(MovimientoRemoto.class));
	}

	private Transferencia pendienteDeCompensar() {
		Transferencia pendiente = new Transferencia(ORIGEN, DESTINO, MONTO);
		ReflectionTestUtils.setField(pendiente, "id", ID);
		pendiente.marcarCargada(new BigDecimal("800"));
		pendiente.marcarCompensacionPendiente("cuenta no contesto");
		return pendiente;
	}

	@Test
	void elCaminoFelizGuardaCadaPasoYTerminaCompletada() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono")).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		ledgerQueAcepta();

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(COMPLETADA);
		assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, ABONADA, COMPLETADA);
		verify(movimientos).registrar(eq("transferencia-12-asiento-cargo"), eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
			eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		verify(movimientos).registrar(eq("transferencia-12-asiento-abono"), eq(DESTINO), eq(TipoMovimiento.TRANSFERENCIA_RECIBIDA),
			eq(MONTO), eq(new BigDecimal("700")), any());
		verify(eventos).publicar(any(EventoTransferencia.class)); // un evento, ya sin llamadas HTTP a notificacion
		verify(cuentas, never()).abonar(eq(ORIGEN), any(), any()); // no hubo nada que devolver
	}

	@Test
	void transferirAUnaCuentaQueNoExisteNoCobraNada() {
		when(cuentas.consultar(ORIGEN)).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		when(cuentas.consultar(DESTINO)).thenThrow(new ErrorRemotoException("cuenta", 404, "No existe la CLABE " + DESTINO));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class)
			.extracting("estado").isEqualTo(404);
		verify(cuentas, never()).cargar(any(), any(), any());
		verifyNoInteractions(movimientos, transferencias, eventos);
	}

	@Test
	void transferirALaMismaCuentaSeRechaza() {
		assertThatThrownBy(() -> servicio.transferir(ORIGEN, ORIGEN, MONTO)).isInstanceOf(ReglaDeNegocioException.class);
		verifyNoInteractions(cuentas, movimientos, transferencias, eventos);
	}

	/** Si el cargo no se hizo (saldo insuficiente), no hay nada que compensar: queda FALLIDA y el 422 sigue siendo 422. */
	@Test
	void siElCargoSeRechazaQuedaFallidaSinCompensarNada() {
		repositorioQueAnota();
		when(cuentas.cargar(ORIGEN, MONTO, "transferencia-12-cargo"))
			.thenThrow(new ErrorRemotoException("cuenta", 422, "Saldo insuficiente en la CLABE " + ORIGEN));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO))
			.isInstanceOf(ErrorRemotoException.class)
			.extracting("estado").isEqualTo(422);
		assertThat(estadosGuardados).containsExactly(INICIADA, FALLIDA);
		verify(cuentas, never()).abonar(any(), any(), any());
		verifyNoInteractions(movimientos, eventos); // FALLIDA: no se movio dinero, no hay nada que avisar
	}

	/** Lo que en v06 se dejaba roto: ahora, si el abono falla, el orquestador le regresa el dinero a origen. */
	@Test
	void siElAbonoFallaSeDevuelveElCargoYQuedaCompensada() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono")).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion")).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		ledgerQueAcepta();

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(COMPENSADA);
		assertThat(t.getMotivo()).contains("cuenta no contesto");
		assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, COMPENSADA);
		// En el ledger queda la historia completa del origen: salio el cargo y regreso la devolucion.
		InOrder enOrden = inOrder(movimientos);
		enOrden.verify(movimientos).registrar(eq("transferencia-12-asiento-cargo"), eq(ORIGEN), eq(TipoMovimiento.TRANSFERENCIA_ENVIADA),
			eq(MONTO.negate()), eq(new BigDecimal("800")), any());
		enOrden.verify(movimientos).registrar(eq("transferencia-12-asiento-devolucion"), eq(ORIGEN), eq(TipoMovimiento.DEVOLUCION_TRANSFERENCIA),
			eq(MONTO), eq(new BigDecimal("1000")), any());
	}

	/** FALLAR_ABONO_A_CLABE: el abono a esa CLABE falla sin llamar a cuenta, para ver la compensacion con todo arriba. */
	@Test
	void laFallaProvocadaDelAbonoNoLlamaACuentaYSeCompensa() {
		servicio = nuevoServicio(DESTINO);
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion")).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		ledgerQueAcepta();

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(COMPENSADA);
		assertThat(t.getMotivo()).contains("FALLAR_ABONO_A_CLABE");
		verify(cuentas, never()).abonar(eq(DESTINO), any(), any());
	}

	/** La Parte 0 del lab: cuenta se apaga despues del cargo. Ni el abono ni la devolucion pasan. */
	@Test
	void siTambienFallaLaDevolucionQuedaCompensacionPendiente() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(any(), any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
		assertThat(t.getIntentosDeCompensacion()).isEqualTo(1);
		assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, COMPENSACION_PENDIENTE);
		verify(cuentas).abonar(ORIGEN, MONTO, "transferencia-12-devolucion");
	}

	/** El reintento programado vuelve a mandar la devolucion CON LA MISMA clave: cuenta no la aplica dos veces. */
	@Test
	void elReintentoTerminaLaCompensacionPendienteConLaMismaClave() {
		Transferencia pendiente = pendienteDeCompensar();
		when(transferencias.findByEstado(COMPENSACION_PENDIENTE)).thenReturn(List.of(pendiente));
		repositorioQueAnota();
		when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion")).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		ledgerQueAcepta();

		int terminadas = servicio.reintentarCompensacionesPendientes();

		assertThat(terminadas).isEqualTo(1);
		assertThat(pendiente.getEstado()).isEqualTo(COMPENSADA);
		assertThat(pendiente.getIntentosDeCompensacion()).isEqualTo(2);
		verify(cuentas).abonar(ORIGEN, MONTO, "transferencia-12-devolucion");
	}

	@Test
	void unReintentoQueVuelveAFallarSigueEnPendienteYCuentaElIntento() {
		Transferencia pendiente = pendienteDeCompensar();
		when(transferencias.findByEstado(COMPENSACION_PENDIENTE)).thenReturn(List.of(pendiente));
		repositorioQueAnota();
		when(cuentas.abonar(any(), any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

		int terminadas = servicio.reintentarCompensacionesPendientes();

		assertThat(terminadas).isZero();
		assertThat(pendiente.getEstado()).isEqualTo(COMPENSACION_PENDIENTE);
		assertThat(pendiente.getIntentosDeCompensacion()).isEqualTo(2);
	}

	/** Despues del abono ya no se compensa (el dinero llego): si falla el ledger, la fila se queda en ABONADA. */
	@Test
	void siFallaElLedgerDespuesDelAbonoNoSeDevuelveNadaYQuedaAbonada() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono")).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		when(movimientos.registrar(anyString(), any(), any(), any(), any(), any())).thenThrow(new ResourceAccessException("movimiento caido"));

		assertThatThrownBy(() -> servicio.transferir(ORIGEN, DESTINO, MONTO)).isInstanceOf(ResourceAccessException.class);
		assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, ABONADA);
		verify(cuentas, never()).abonar(eq(ORIGEN), any(), any());
	}

	// ---- v09: los eventos que publica la saga ----

	/** Lo que el evento lleva sale de la saga: los dos saldos que contesto cuenta y la hora del ultimo paso. */
	@Test
	void alCompletarsePublicaTransferenciaRealizadaConLosSaldosDeLaSaga() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono")).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		ledgerQueAcepta();

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		EventoTransferencia evento = eventoPublicado();
		assertThat(evento.tipo()).isEqualTo("TransferenciaRealizada");
		assertThat(evento.routingKey()).isEqualTo("transferencia.realizada");
		assertThat(evento.eventoId()).isNotNull().isEqualTo(t.getEventoId());
		assertThat(evento.transferenciaId()).isEqualTo(ID);
		assertThat(evento.clabeOrigen()).isEqualTo(ORIGEN);
		assertThat(evento.clabeDestino()).isEqualTo(DESTINO);
		assertThat(evento.monto()).isEqualTo(MONTO);
		assertThat(evento.saldoOrigen()).isEqualTo(new BigDecimal("800"));
		assertThat(evento.saldoDestino()).isEqualTo(new BigDecimal("700"));
		assertThat(evento.fecha()).isEqualTo(t.getActualizada());
	}

	/** Se publica DESPUES de guardar COMPLETADA: si el proceso cae antes, la base no dice algo que no paso. */
	@Test
	void elEventoSaleDespuesDeGuardarCompletada() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono")).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		ledgerQueAcepta();
		doAnswer(i -> {
			assertThat(estadosGuardados).last().isEqualTo(COMPLETADA);
			return null;
		}).when(eventos).publicar(any());

		servicio.transferir(ORIGEN, DESTINO, MONTO);

		verify(eventos).publicar(any());
	}

	/** El cliente ya no depende de que el aviso salga: si publicar falla, la transferencia sigue COMPLETADA. */
	@Test
	void siPublicarFallaLaTransferenciaSigueCompletadaYNoSeRevierte() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono")).thenReturn(new CuentaRemota(DESTINO, "Beto", new BigDecimal("700")));
		ledgerQueAcepta();
		doThrow(new EventoNoPublicadoException("el broker no confirmo")).when(eventos).publicar(any());

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		assertThat(t.getEstado()).isEqualTo(COMPLETADA);
		assertThat(estadosGuardados).containsExactly(INICIADA, CARGADA, ABONADA, COMPLETADA);
		verify(cuentas, never()).abonar(eq(ORIGEN), any(), any());
	}

	/** En la revertida, el saldo de origen es el de despues de la devolucion; el de destino la saga no lo conoce. */
	@Test
	void alCompensarsePublicaTransferenciaRevertidaConElSaldoTrasLaDevolucion() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(DESTINO, MONTO, "transferencia-12-abono")).thenThrow(new ErrorRemotoException("cuenta", 503, "cuenta no contesto"));
		when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion")).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		ledgerQueAcepta();

		Transferencia t = servicio.transferir(ORIGEN, DESTINO, MONTO);

		EventoTransferencia evento = eventoPublicado();
		assertThat(evento.tipo()).isEqualTo("TransferenciaRevertida");
		assertThat(evento.routingKey()).isEqualTo("transferencia.revertida");
		assertThat(evento.eventoId()).isEqualTo(t.getEventoId());
		assertThat(evento.saldoOrigen()).isEqualTo(new BigDecimal("1000"));
		assertThat(evento.saldoDestino()).isNull();
		assertThat(evento.monto()).isEqualTo(MONTO);
	}

	/** Mientras la devolucion no se haga no hay nada que avisar: el aviso saldria antes que el dinero. */
	@Test
	void siLaCompensacionQuedaPendienteNoSePublicaNada() {
		repositorioQueAnota();
		cargoExitoso();
		when(cuentas.abonar(any(), any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

		servicio.transferir(ORIGEN, DESTINO, MONTO);

		verifyNoInteractions(eventos);
	}

	/** El reintento programado pasa por el mismo compensar(): cuando por fin la termina, tambien avisa. */
	@Test
	void elReintentoQueTerminaLaCompensacionPublicaLaRevertida() {
		Transferencia pendiente = pendienteDeCompensar();
		when(transferencias.findByEstado(COMPENSACION_PENDIENTE)).thenReturn(List.of(pendiente));
		repositorioQueAnota();
		when(cuentas.abonar(ORIGEN, MONTO, "transferencia-12-devolucion")).thenReturn(new CuentaRemota(ORIGEN, "Ana", new BigDecimal("1000")));
		ledgerQueAcepta();

		servicio.reintentarCompensacionesPendientes();

		EventoTransferencia evento = eventoPublicado();
		assertThat(evento.tipo()).isEqualTo("TransferenciaRevertida");
		assertThat(evento.eventoId()).isEqualTo(pendiente.getEventoId());
	}

	private EventoTransferencia eventoPublicado() {
		ArgumentCaptor<EventoTransferencia> evento = ArgumentCaptor.forClass(EventoTransferencia.class);
		verify(eventos).publicar(evento.capture());
		return evento.getValue();
	}
}
