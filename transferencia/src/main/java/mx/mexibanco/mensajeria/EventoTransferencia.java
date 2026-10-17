package mx.mexibanco.mensajeria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * v09: el evento que transferencia publica cuando la saga TERMINA. Es un hecho en pasado ("ya se
 * hizo"), no una orden: transferencia no sabe quien lo escucha ni le importa. Hoy lo escucha
 * notificacion; manana podria escucharlo tambien un servicio de antifraude sin tocar este codigo.
 *
 * Es el contrato con los consumidores, asi que es un record propio y no la entidad Transferencia:
 * cambiar una columna de la tabla no debe romper a nadie afuera. Viaja como JSON (ver
 * ConfiguracionMensajeria) con estos mismos nombres de campo.
 *
 *  - eventoId: nace con la transferencia y no cambia (ver Transferencia.getEventoId). Si el mismo
 *    evento se publica o se entrega dos veces, el consumidor lo reconoce por aqui y no duplica.
 *  - tipo: TransferenciaRealizada o TransferenciaRevertida.
 *  - saldoOrigen y saldoDestino: los que la saga conoce porque cuenta se los contesto. En la
 *    revertida, saldoOrigen es el de despues de la devolucion y saldoDestino va null (no se toco).
 *  - fecha: cuando la saga llego al estado final (ISO-8601 en el JSON).
 */
public record EventoTransferencia(UUID eventoId, String tipo, Long transferenciaId, String clabeOrigen,
								  String clabeDestino, BigDecimal monto, BigDecimal saldoOrigen,
								  BigDecimal saldoDestino, Instant fecha) {

	public static final String REALIZADA = "TransferenciaRealizada";
	public static final String REVERTIDA = "TransferenciaRevertida";

	/** La saga llego a COMPLETADA: el dinero salio de origen y llego a destino. */
	public static EventoTransferencia realizada(UUID eventoId, Long transferenciaId, String clabeOrigen, String clabeDestino,
												BigDecimal monto, BigDecimal saldoOrigen, BigDecimal saldoDestino, Instant fecha) {
		return new EventoTransferencia(eventoId, REALIZADA, transferenciaId, clabeOrigen, clabeDestino, monto,
			saldoOrigen, saldoDestino, fecha);
	}

	/** La saga llego a COMPENSADA: el abono fallo y el cargo ya se devolvio a origen. */
	public static EventoTransferencia revertida(UUID eventoId, Long transferenciaId, String clabeOrigen, String clabeDestino,
												BigDecimal monto, BigDecimal saldoOrigenTrasDevolucion, Instant fecha) {
		return new EventoTransferencia(eventoId, REVERTIDA, transferenciaId, clabeOrigen, clabeDestino, monto,
			saldoOrigenTrasDevolucion, null, fecha);
	}

	/**
	 * La routing key con la que se publica en el exchange topic "transferencias". Notificacion se
	 * suscribe con el patron transferencia.* y recibe las dos; otro consumidor podria pedir solo
	 * transferencia.revertida.
	 */
	public String routingKey() {
		return REVERTIDA.equals(tipo) ? "transferencia.revertida" : "transferencia.realizada";
	}
}
