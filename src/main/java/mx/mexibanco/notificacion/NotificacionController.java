package mx.mexibanco.notificacion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bandeja del cliente: GET /notificaciones?clabe=... , de la mas reciente a la mas vieja. */
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
}
