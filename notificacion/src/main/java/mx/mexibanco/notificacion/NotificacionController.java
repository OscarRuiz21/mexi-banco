package mx.mexibanco.notificacion;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /notificaciones?clabe=... es la bandeja del cliente, de la mas reciente a la mas vieja.
 * POST /notificaciones es NUEVO en v06a y es interno: lo usan transferencia y spei, que en el
 * monolito llamaban a NotificacionService.notificar(...) como metodo.
 */
@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

	private final NotificacionService notificaciones;

	public NotificacionController(NotificacionService notificaciones) {
		this.notificaciones = notificaciones;
	}

	@GetMapping
	public List<Notificacion> bandeja(@RequestParam String clabe) {
		return notificaciones.bandeja(clabe);
	}

	/** Interno: avisa de un movimiento ya registrado. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Notificacion notificar(@Valid @RequestBody AvisoDeMovimiento movimiento) {
		return notificaciones.notificar(movimiento);
	}
}
