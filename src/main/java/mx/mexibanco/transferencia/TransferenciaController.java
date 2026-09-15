package mx.mexibanco.transferencia;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import mx.mexibanco.cuenta.Cuenta;
import mx.mexibanco.cuenta.CuentaRepository;
import mx.mexibanco.movimiento.Movimiento;
import mx.mexibanco.movimiento.MovimientoRepository;
import mx.mexibanco.movimiento.TipoMovimiento;
import mx.mexibanco.notificacion.NotificacionService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Tercer concepto de Mexi Banco: transferencia INTERNA, entre dos cuentas del mismo banco.
 * Ambos movimientos (cargo y abono) se confirman juntos o ninguno: una sola transaccion local,
 * a proposito distinto de /spei, que cruza a otro banco y no puede darse ese lujo.
 */
@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final CuentaRepository cuentas;
	private final MovimientoRepository movimientos;
	private final NotificacionService notificaciones;

	public TransferenciaController(CuentaRepository cuentas, MovimientoRepository movimientos, NotificacionService notificaciones) {
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen, @NotBlank String claveDestino, @Positive BigDecimal monto) {
	}

	@PostMapping
	@Transactional
	@ResponseStatus(HttpStatus.CREATED)
	public void transferir(@RequestBody SolicitudTransferencia solicitud) {
		Cuenta origen = cuentas.findByClabe(solicitud.claveOrigen())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la cuenta origen"));
		Cuenta destino = cuentas.findByClabe(solicitud.claveDestino())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la cuenta destino"));

		if (origen.getSaldo().compareTo(solicitud.monto()) < 0) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Saldo insuficiente");
		}

		origen.aplicar(solicitud.monto().negate());
		destino.aplicar(solicitud.monto());

		Movimiento cargo = movimientos.save(new Movimiento(origen.getClabe(), TipoMovimiento.TRANSFERENCIA_ENVIADA,
			solicitud.monto().negate(), origen.getSaldo(), "a " + destino.getClabe()));
		Movimiento abono = movimientos.save(new Movimiento(destino.getClabe(), TipoMovimiento.TRANSFERENCIA_RECIBIDA,
			solicitud.monto(), destino.getSaldo(), "de " + origen.getClabe()));

		notificaciones.notificar(cargo);
		notificaciones.notificar(abono);
	}
}
