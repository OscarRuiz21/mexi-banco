package mx.mexibanco.notificacion;

import mx.mexibanco.movimiento.Movimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cuarto concepto de Mexi Banco: hoy es un stub que solo escribe en el log.
 * A proposito no habla con nada mas: la version de esta clase que si publica a un broker
 * real (RabbitMQ/Kafka) se agrega sesion a sesion, cuando toque esa pieza en el temario
 * (ver README.md, "Roadmap"). No adelantar esa parte aqui.
 */
@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

	public void notificar(Movimiento movimiento) {
		log.info("[notificacion-stub] {} · {} · {} · saldo resultante {}",
			movimiento.getClabeCuenta(), movimiento.getTipo(), movimiento.getMonto(), movimiento.getSaldoResultante());
	}
}
