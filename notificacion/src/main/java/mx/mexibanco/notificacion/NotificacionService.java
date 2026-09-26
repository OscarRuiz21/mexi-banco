package mx.mexibanco.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cuarto concepto de Mexi Banco. Guarda el aviso en su base y lo escribe en el log. En el monolito
 * esto ocurria dentro de la misma transaccion que el movimiento; en v06a es una llamada HTTP aparte
 * y ya no hay transaccion que las una. La version que publica a un broker real (RabbitMQ/Kafka)
 * llega en una sesion posterior; no adelantarla aqui.
 */
@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

	private final NotificacionRepository notificaciones;

	public NotificacionService(NotificacionRepository notificaciones) {
		this.notificaciones = notificaciones;
	}

	@Transactional
	public Notificacion notificar(AvisoDeMovimiento movimiento) {
		String mensaje = movimiento.tipo() + " por " + movimiento.monto().abs() + ", saldo resultante " + movimiento.saldoResultante();
		log.info("[notificacion] {} · {}", movimiento.clabeCuenta(), mensaje);
		return notificaciones.save(new Notificacion(movimiento.clabeCuenta(), mensaje));
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
