package mx.mexibanco.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuarto concepto de Mexi Banco. Guarda el aviso en su base y lo escribe en el log. En el monolito
 * esto ocurria dentro de la misma transaccion que el movimiento; en v06a es una llamada HTTP aparte
 * y ya no hay transaccion que las una.
 *
 * v09: tambien guarda los avisos de las transferencias que llegan como eventos por RabbitMQ (los
 * recibe mensajeria.ConsumidorDeTransferencias). POST /notificaciones se queda para spei, que sigue
 * avisando por HTTP.
 */
@Service
public class NotificacionService {

	private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

	private final NotificacionRepository notificaciones;
	private final EventoProcesadoRepository procesados;

	public NotificacionService(NotificacionRepository notificaciones, EventoProcesadoRepository procesados) {
		this.notificaciones = notificaciones;
		this.procesados = procesados;
	}

	@Transactional
	public Notificacion notificar(AvisoDeMovimiento movimiento) {
		return notificaciones.save(aviso(movimiento.clabeCuenta(), movimiento.tipo(), movimiento.monto(), movimiento.saldoResultante()));
	}

	/**
	 * v09: los avisos de una transferencia terminada. TransferenciaRealizada son dos (a quien envio y a
	 * quien recibio); TransferenciaRevertida es uno, a origen, porque su dinero regreso.
	 *
	 * IDEMPOTENTE: lo primero es poner la marca del eventoId, en la misma transaccion que los avisos.
	 * Si la marca ya estaba (el mismo evento por segunda vez), no se guarda nada y regresa una lista
	 * vacia. Si algo falla despues de la marca, el rollback se lleva marca y avisos juntos, y la
	 * siguiente entrega lo intenta desde cero.
	 */
	@Transactional
	public List<Notificacion> avisarDeTransferencia(EventoTransferencia evento) {
		if (procesados.marcarSiEsNuevo(evento.eventoId(), evento.tipo(), evento.transferenciaId()) == 0) {
			log.info("[notificacion] evento {} de la transferencia {} repetido: ya se aviso, no se guarda nada",
				evento.eventoId(), evento.transferenciaId());
			return List.of();
		}
		List<Notificacion> avisos = switch (evento.tipo()) {
			case EventoTransferencia.REALIZADA -> List.of(
				aviso(evento.clabeOrigen(), "TRANSFERENCIA_ENVIADA", evento.monto(), evento.saldoOrigen()),
				aviso(evento.clabeDestino(), "TRANSFERENCIA_RECIBIDA", evento.monto(), evento.saldoDestino()));
			case EventoTransferencia.REVERTIDA -> List.of(
				aviso(evento.clabeOrigen(), "DEVOLUCION_TRANSFERENCIA", evento.monto(), evento.saldoOrigen()));
			default -> throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
		};
		return notificaciones.saveAll(avisos);
	}

	/** El mismo texto de siempre: tipo, monto sin signo y saldo resultante. */
	private static Notificacion aviso(String clabe, String tipo, BigDecimal monto, BigDecimal saldo) {
		String mensaje = tipo + " por " + monto.abs() + ", saldo resultante " + saldo;
		log.info("[notificacion] {} · {}", clabe, mensaje);
		return new Notificacion(clabe, mensaje);
	}

	@Transactional(readOnly = true)
	public List<Notificacion> bandeja(String clabeCuenta) {
		return notificaciones.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
