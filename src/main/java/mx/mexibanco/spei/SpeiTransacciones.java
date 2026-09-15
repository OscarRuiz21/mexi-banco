package mx.mexibanco.spei;

import mx.mexibanco.cuenta.Cuenta;
import mx.mexibanco.cuenta.CuentaRepository;
import mx.mexibanco.movimiento.Movimiento;
import mx.mexibanco.movimiento.MovimientoRepository;
import mx.mexibanco.movimiento.TipoMovimiento;
import mx.mexibanco.notificacion.NotificacionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Separado de SpeiService a proposito: @Transactional no funciona en una auto-invocacion
 * (this.metodo() dentro de la misma clase salta el proxy de Spring). Dos beans distintos,
 * dos transacciones distintas de verdad.
 */
@Component
class SpeiTransacciones {

	private final SpeiSolicitudRepository solicitudes;
	private final CuentaRepository cuentas;
	private final MovimientoRepository movimientos;
	private final NotificacionService notificaciones;

	SpeiTransacciones(SpeiSolicitudRepository solicitudes, CuentaRepository cuentas,
					   MovimientoRepository movimientos, NotificacionService notificaciones) {
		this.solicitudes = solicitudes;
		this.cuentas = cuentas;
		this.movimientos = movimientos;
		this.notificaciones = notificaciones;
	}

	/**
	 * Si el UNIQUE choca, la excepcion sale de aqui a proposito: JPA marca la transaccion como
	 * rollback-only y atraparla adentro terminaria en UnexpectedRollbackException al confirmar.
	 * La atrapa SpeiService, ya fuera de la transaccion.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	SpeiSolicitud reservar(String idempotencyKey, SpeiService.SolicitudSpei datos) {
		SpeiSolicitud nueva = new SpeiSolicitud(idempotencyKey, datos.claveOrigen(), datos.bancoDestino(),
			datos.claveDestino(), datos.monto());
		return solicitudes.saveAndFlush(nueva);
	}

	@Transactional
	SpeiSolicitud ejecutar(SpeiSolicitud solicitud) {
		Cuenta origen = cuentas.findByClabe(solicitud.getClaveOrigen())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la cuenta origen"));
		if (origen.getSaldo().compareTo(solicitud.getMonto()) < 0) {
			throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Saldo insuficiente");
		}

		origen.aplicar(solicitud.getMonto().negate());
		Movimiento movimiento = movimientos.save(new Movimiento(origen.getClabe(), TipoMovimiento.SPEI_ENVIADO,
			solicitud.getMonto().negate(), origen.getSaldo(),
			solicitud.getBancoDestino() + " · " + solicitud.getClaveDestino()));

		simularLatenciaDeOtroBanco();

		// La solicitud llego de otra transaccion (REQUIRES_NEW), asi que aqui esta detached:
		// sin este save, el ENVIADO se queda en memoria y la fila sigue en PROCESANDO.
		solicitud.marcarEnviado();
		SpeiSolicitud enviada = solicitudes.save(solicitud);
		notificaciones.notificar(movimiento);
		return enviada;
	}

	private void simularLatenciaDeOtroBanco() {
		try {
			Thread.sleep(400);
		} catch (InterruptedException interrumpido) {
			Thread.currentThread().interrupt();
		}
	}
}
