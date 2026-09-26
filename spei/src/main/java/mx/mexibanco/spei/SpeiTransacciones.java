package mx.mexibanco.spei;

import mx.mexibanco.clientes.CuentaCliente;
import mx.mexibanco.clientes.CuentaRemota;
import mx.mexibanco.clientes.MovimientoCliente;
import mx.mexibanco.clientes.MovimientoRemoto;
import mx.mexibanco.clientes.NotificacionCliente;
import mx.mexibanco.clientes.TipoMovimiento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separado de SpeiService a proposito: @Transactional no funciona en una auto-invocacion
 * (this.metodo() dentro de la misma clase salta el proxy de Spring). Dos beans distintos,
 * dos transacciones distintas de verdad.
 *
 * En v06a solo la reserva y el cambio a ENVIADO son locales (base "spei"). El cargo, el asiento y
 * el aviso son llamadas HTTP a cuenta, movimiento y notificacion, que se confirman cada una sola.
 */
@Component
class SpeiTransacciones {

	private final SpeiSolicitudRepository solicitudes;
	private final CuentaCliente cuentas;
	private final MovimientoCliente movimientos;
	private final NotificacionCliente notificaciones;

	SpeiTransacciones(SpeiSolicitudRepository solicitudes, CuentaCliente cuentas,
					   MovimientoCliente movimientos, NotificacionCliente notificaciones) {
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

	/**
	 * Sin @Transactional: no hay nada local que proteger hasta el save del final, y una transaccion
	 * abierta mientras se espera la red solo retendria una conexion a la base. Si el cargo sale
	 * bien y movimiento falla, el dinero ya salio y la solicitud se queda en PROCESANDO: un
	 * reintento con la misma clave responde 409. Tambien eso se lo lleva la saga de la S12.
	 */
	SpeiSolicitud ejecutar(SpeiSolicitud solicitud) {
		CuentaRemota origen = cuentas.cargar(solicitud.getClaveOrigen(), solicitud.getMonto());
		MovimientoRemoto movimiento = movimientos.registrar(origen.clabe(), TipoMovimiento.SPEI_ENVIADO,
			solicitud.getMonto().negate(), origen.saldo(),
			solicitud.getBancoDestino() + " · " + solicitud.getClaveDestino());

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
