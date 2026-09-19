package mx.mexibanco.cuenta;

import mx.mexibanco.compartido.ConflictoException;
import mx.mexibanco.compartido.NoEncontradoException;
import mx.mexibanco.compartido.ReglaDeNegocioException;
import mx.mexibanco.movimiento.MovimientoService;
import mx.mexibanco.movimiento.TipoMovimiento;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** El servicio se prueba solo, sin HTTP ni base: el repositorio y el ledger son dobles. */
@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

	private static final String CLABE = "002180000000000001";

	@Mock
	CuentaRepository cuentas;

	@Mock
	MovimientoService movimientos;

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

		assertThatThrownBy(() -> servicio.cargar(CLABE, new BigDecimal("100.01"))).isInstanceOf(ReglaDeNegocioException.class);
		assertThat(cuenta.getSaldo()).isEqualByComparingTo("100");
	}

	@Test
	void cargarYAbonarMuevenElSaldo() {
		Cuenta cuenta = new Cuenta(CLABE, "Ana", new BigDecimal("100"));
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.of(cuenta));

		servicio.cargar(CLABE, new BigDecimal("30"));
		servicio.abonar(CLABE, new BigDecimal("5"));

		assertThat(cuenta.getSaldo()).isEqualByComparingTo("75");
	}

	@Test
	void consultarUnaClabeQueNoExisteEsNoEncontrado() {
		when(cuentas.findByClabe(CLABE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.consultar(CLABE)).isInstanceOf(NoEncontradoException.class);
	}
}
