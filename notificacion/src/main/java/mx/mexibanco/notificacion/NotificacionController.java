package mx.mexibanco.notificacion;

import jakarta.validation.Valid;
import mx.mexibanco.notificacion.avisos.BitacoraDeAvisos;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /notificaciones?clabe=... es la bandeja del cliente, de la mas reciente a la mas vieja.
 * POST /notificaciones es NUEVO en v06a y es interno: lo usan transferencia y spei, que en el
 * monolito llamaban a NotificacionService.notificar(...) como metodo. Desde v09 solo lo usa spei:
 * transferencia avisa con eventos en RabbitMQ (ver mensajeria.ConsumidorDeTransferencias).
 *
 * v11: GET /notificaciones/{clabe} es la bitacora de avisos en MongoDB: los ultimos 10, cada uno con
 * la forma de su canal. La bandeja vieja (GET ?clabe=, en Postgres) se queda para los avisos de spei.
 */
@RestController
@RequestMapping("/notificaciones")
public class NotificacionController {

	private final NotificacionService notificaciones;
	private final BitacoraDeAvisos bitacora;

	public NotificacionController(NotificacionService notificaciones, BitacoraDeAvisos bitacora) {
		this.notificaciones = notificaciones;
		this.bitacora = bitacora;
	}

	@GetMapping
	public List<Notificacion> bandeja(@RequestParam String clabe) {
		return notificaciones.bandeja(clabe);
	}

	/** v11: los ultimos avisos de la CLABE, tal como estan en la coleccion avisos de MongoDB. */
	@GetMapping("/{clabe}")
	public List<Document> avisos(@PathVariable String clabe) {
		return bitacora.ultimos(clabe);
	}

	/** Interno: avisa de un movimiento ya registrado. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Notificacion notificar(@Valid @RequestBody AvisoDeMovimiento movimiento) {
		return notificaciones.notificar(movimiento);
	}
}
