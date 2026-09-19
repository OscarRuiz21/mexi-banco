package mx.mexibanco.notificacion;

import mx.mexibanco.movimiento.Movimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cuarto concepto de Mexi Banco. Hoy guarda el aviso en su tabla y lo escribe en el log, dentro de
 * la misma transaccion que el movimiento. A proposito no habla con nada mas: la version que publica
 * a un broker real (RabbitMQ/Kafka) se agrega sesion a sesion (ver README.md, "Roadmap").
 * No adelantar esa parte aqui.
 */
@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

	private final NotificacionRepository notificaciones;

	public NotificacionService(NotificacionRepository notificaciones) {
		this.notificaciones = notificaciones;
	}

	@Transactional
	public Notificacion notificar(Movimiento movimiento) {
		String mensaje = movimiento.getTipo() + " por " + movimiento.getMonto().abs() + ", saldo resultante " + movimiento.getSaldoResultante();
		log.info("[notificacion] {} · {}", movimiento.getClabeCuenta(), mensaje);
		return notificaciones.save(new Notificacion(movimiento.getClabeCuenta(), mensaje));
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
