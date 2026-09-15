package mx.mexibanco.spei;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class SpeiService {

	private final SpeiSolicitudRepository solicitudes;
	private final SpeiTransacciones transacciones;

	public SpeiService(SpeiSolicitudRepository solicitudes, SpeiTransacciones transacciones) {
		this.solicitudes = solicitudes;
		this.transacciones = transacciones;
	}

	public record SolicitudSpei(@NotBlank String claveOrigen, @NotBlank String bancoDestino,
								 @NotBlank String claveDestino, @Positive BigDecimal monto) {
	}

	/**
	 * El INSERT (via saveAndFlush, en SpeiTransacciones.reservar) es la comprobacion y la
	 * reserva al mismo tiempo, y ocurre antes de cobrar: exactamente el patron de la T01
	 * (Idempotency-Key, RFC 9110, Stripe). Si choca contra el UNIQUE, no se manda dos veces.
	 */
	public SpeiSolicitud procesar(String idempotencyKey, SolicitudSpei datos) {
		SpeiSolicitud reservada;
		try {
			reservada = transacciones.reservar(idempotencyKey, datos);
		} catch (DataIntegrityViolationException choqueDeUnique) {
			reservada = null;
		}
		if (reservada != null) {
			return transacciones.ejecutar(reservada);
		}

		SpeiSolicitud existente = solicitudes.findByIdempotencyKey(idempotencyKey)
			.orElseThrow(() -> new IllegalStateException("La reserva choco pero no aparece: revisar la transaccion"));

		if (existente.getEstado() == EstadoSpei.PROCESANDO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Ya hay un SPEI en vuelo con esta Idempotency-Key");
		}
		return existente; // reintento con la misma clave, ya enviado: se regresa lo mismo, sin cobrar otra vez
	}
}
