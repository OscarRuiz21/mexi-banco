package mx.mexibanco.cuenta;

import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** El servicio se prueba solo, sin HTTP ni base: el repositorio y el cliente del ledger son dobles. */
@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

	private static final String CLABE = "002180000000000001";

	@Mock
	CuentaRepository cuentas;

	@Mock
	MovimientoCliente movimientos;

	@Mock
	OperacionAplicadaRepository operaciones;

	@InjectMocks
	CuentaService servicio;

	@Test
	void abrirConSaldoRegistraElDepositoEnElLedger() {
		when(cuentas.existsByClabe(CLABE)).thenReturn(false);
		when(cuentas.save(any(Cuenta.class))).thenAnswer(i -> i.getArgument(0));

		Cuenta cuenta = servicio.abrir(CLABE, "Ana", new BigDecimal("1000"));

		assertThat(cuenta.getSaldo()).isEqualByComparingTo("1000");
		verify(movimientos).registrar(CLABE, TipoMovimiento.DEPOSITO, new BigDecimal("1000"), new BigDecimal("1000"), "saldo inicial");
	}

	@Test
	void abrirEnCeroNoInventaMovimientos() {
		when(cuentas.existsByClabe(CLABE)).thenReturn(false);
		when(cuentas.save(any(Cuenta.class))).thenAnswer(i -> i.getArgument(0));

		servicio.abrir(CLABE, "Ana", BigDecimal.ZERO);

		verifyNoInteractions(movimientos);
	}

	@Test
	void abrirUnaClabeRepetidaEsConflicto() {
		when(cuentas.existsByClabe(CLABE)).thenReturn(true);

		assertThatThrownBy(() -> servicio.abrir(CLABE, "Ana", BigDecimal.TEN)).isInstanceOf(ConflictoException.class);
		verify(cuentas, never()).save(any());
	}

	@Test
	void cargarSinSaldoSuficienteNoTocaElSaldo() {
		Cuenta cuenta = new Cuenta(CLABE, "Ana", new BigDecimal("100"));
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.of(cuenta));

		assertThatThrownBy(() -> servicio.cargar(CLABE, new BigDecimal("100.01"), null)).isInstanceOf(ReglaDeNegocioException.class);
		assertThat(cuenta.getSaldo()).isEqualByComparingTo("100");
	}

	@Test
	void cargarYAbonarMuevenElSaldo() {
		Cuenta cuenta = new Cuenta(CLABE, "Ana", new BigDecimal("100"));
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.of(cuenta));

		servicio.cargar(CLABE, new BigDecimal("30"), null);
		servicio.abonar(CLABE, new BigDecimal("5"), null);

		assertThat(cuenta.getSaldo()).isEqualByComparingTo("75");
	}

	/** v08: la devolucion de la saga se reintenta con la misma clave; el saldo solo se mueve la primera vez. */
	@Test
	void abonarDosVecesConLaMismaClaveSoloSumaUnaVezYRepiteLaRespuesta() {
		Cuenta cuenta = new Cuenta(CLABE, "Ana", new BigDecimal("800"));
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.of(cuenta));
		operacionesEnMemoria();

		SaldoAplicado primera = servicio.abonar(CLABE, new BigDecimal("200"), "transferencia-12-devolucion");
		SaldoAplicado repetida = servicio.abonar(CLABE, new BigDecimal("200"), "transferencia-12-devolucion");

		assertThat(cuenta.getSaldo()).isEqualByComparingTo("1000");
		assertThat(primera.saldo()).isEqualByComparingTo("1000");
		assertThat(repetida.saldo()).isEqualByComparingTo("1000");
		verify(operaciones, times(1)).save(any(OperacionAplicada.class));
	}

	@Test
	void cargarDosVecesConLaMismaClaveSoloRestaUnaVez() {
		Cuenta cuenta = new Cuenta(CLABE, "Ana", new BigDecimal("1000"));
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.of(cuenta));
		operacionesEnMemoria();

		servicio.cargar(CLABE, new BigDecimal("200"), "transferencia-12-cargo");
		servicio.cargar(CLABE, new BigDecimal("200"), "transferencia-12-cargo");

		assertThat(cuenta.getSaldo()).isEqualByComparingTo("800");
	}

	@Test
	void laMismaClaveParaOtraOperacionEsConflictoYNoMueveElSaldo() {
		Cuenta cuenta = new Cuenta(CLABE, "Ana", new BigDecimal("800"));
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.of(cuenta));
		operacionesEnMemoria();
		servicio.abonar(CLABE, new BigDecimal("200"), "clave-1");

		assertThatThrownBy(() -> servicio.abonar(CLABE, new BigDecimal("999"), "clave-1")).isInstanceOf(ConflictoException.class);
		assertThat(cuenta.getSaldo()).isEqualByComparingTo("1000");
	}

	/** Un repositorio de operaciones que recuerda lo que se le guarda, como la tabla real. */
	private void operacionesEnMemoria() {
		Map<String, OperacionAplicada> tabla = new HashMap<>();
		when(operaciones.findByClave(any())).thenAnswer(i -> Optional.ofNullable(tabla.get(i.<String>getArgument(0))));
		when(operaciones.save(any(OperacionAplicada.class))).thenAnswer(i -> {
			OperacionAplicada op = i.getArgument(0);
			tabla.put(op.getClave(), op);
			return op;
		});
	}

	@Test
	void consultarUnaClabeQueNoExisteEsNoEncontrado() {
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.consultar(CLABE)).isInstanceOf(NoEncontradoException.class);
	}
}
