package mx.mexibanco.notificacion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * v09: el evento que transferencia publica en RabbitMQ al terminar una saga, tal como notificacion lo
 * lee de la cola notificacion.transferencias. Es una COPIA LOCAL del contrato (los mismos nombres de
 * campo del JSON), no la clase de transferencia: igual que AvisoDeMovimiento con HTTP, cada servicio
 * tiene la suya. Los campos de mas que algun dia mande transferencia se ignoran.
 *
 * Las anotaciones dicen que es un evento valido. Lo que no lo sea (le falta el eventoId, un tipo que
 * no existe) no se puede procesar por mas que se reintente, y el consumidor lo manda a la DLQ.
 */
public record EventoTransferencia(@NotNull UUID eventoId,
								  @NotNull @Pattern(regexp = REALIZADA + "|" + REVERTIDA) String tipo,
								  @NotNull Long transferenciaId,
								  @NotBlank String clabeOrigen,
								  @NotBlank String clabeDestino,
								  @NotNull @Positive BigDecimal monto,
								  @NotNull BigDecimal saldoOrigen,
								  BigDecimal saldoDestino,
								  @NotNull Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";

	/** En una realizada hay dos avisos y el de destino necesita su saldo; en una revertida no viene. */
	@AssertTrue(message = "una TransferenciaRealizada trae saldoDestino")
	boolean isSaldoDestinoSiEsRealizada() {
		return !REALIZADA.equals(tipo) || saldoDestino != null;
	}
}
