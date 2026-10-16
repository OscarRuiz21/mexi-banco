package mx.mexibanco.transferencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El reintento programado de la saga (v08). Cada REINTENTO_COMPENSACIONES_MS (10 s por omision)
 * busca las transferencias en COMPENSACION_PENDIENTE y vuelve a intentar la devolucion. Mientras
 * cuenta siga caida (o el circuito abierto) fallan rapido y siguen pendientes; cuando vuelve,
 * terminan en COMPENSADA.
 *
 * Reintentar es seguro solo porque la compensacion es idempotente (ver TransferenciaService.compensar):
 * sin la clave, cada vuelta de este reloj podria devolverle el dinero a origen otra vez.
 *
 * Simplificaciones a proposito: espera fija entre vueltas (no creciente) y sin tope de intentos ni
 * alerta a una persona. Con fixedDelay, Spring no empieza una vuelta hasta que termina la anterior;
 * si hubiera dos replicas de transferencia, las dos podrian reintentar la misma fila a la vez y la
 * clave de idempotencia es lo que evita que el dinero se devuelva dos veces.
 */
@Component
public class ReintentoDeCompensaciones {

	private static final Logger log = LoggerFactory.getLogger(ReintentoDeCompensaciones.class);

	private final TransferenciaService transferencias;

	public ReintentoDeCompensaciones(TransferenciaService transferencias) {
		this.transferencias = transferencias;
	}

	@Scheduled(fixedDelayString = "${mexibanco.saga.reintento-compensaciones-ms:10000}",
		initialDelayString = "${mexibanco.saga.reintento-compensaciones-ms:10000}")
	void reintentar() {
		int terminadas = transferencias.reintentarCompensacionesPendientes();
		if (terminadas > 0) {
			log.info("Reintento programado: {} compensacion(es) pendiente(s) terminada(s)", terminadas);
		}
	}
}
