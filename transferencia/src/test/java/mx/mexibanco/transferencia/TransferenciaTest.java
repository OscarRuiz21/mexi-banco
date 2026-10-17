package mx.mexibanco.transferencia;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** v09: el id del evento nace con la transferencia y no cambia, para que el consumidor reconozca un repetido. */
class TransferenciaTest {

	@Test
	void cadaTransferenciaNaceConSuPropioEventoId() {
		Transferencia una = new Transferencia("002180000000000001", "002180000000000002", BigDecimal.TEN);
		Transferencia otra = new Transferencia("002180000000000001", "002180000000000002", BigDecimal.TEN);

		assertThat(una.getEventoId()).isNotNull().isNotEqualTo(otra.getEventoId());
	}

	@Test
	void elEventoIdNoCambiaAlAvanzarLaSaga() {
		Transferencia t = new Transferencia("002180000000000001", "002180000000000002", BigDecimal.TEN);
		UUID alNacer = t.getEventoId();

		t.marcarCargada(new BigDecimal("90"));
		t.marcarAbonada();
		t.marcarCompletada();

		assertThat(t.getEventoId()).isEqualTo(alNacer);
	}

	/** Una fila de v08 (mismo volumen de Postgres) no tiene la columna llena: al leerla se le asigna una. */
	@Test
	void unaFilaViejaSinEventoIdRecibeUnoAlLeerse() {
		Transferencia vieja = new Transferencia("002180000000000001", "002180000000000002", BigDecimal.TEN);
		ReflectionTestUtils.setField(vieja, "eventoId", null);

		vieja.asegurarEventoId();

		assertThat(vieja.getEventoId()).isNotNull();
	}
}
