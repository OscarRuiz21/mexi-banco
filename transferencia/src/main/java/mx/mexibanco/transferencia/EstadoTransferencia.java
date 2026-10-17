package mx.mexibanco.transferencia;

/**
 * En que paso de la saga va una transferencia (v08). Se guarda en la base en CADA paso: si el
 * servicio se cae a la mitad, la fila dice hasta donde llego y que falta.
 *
 * <pre>
 *   INICIADA --cargo ok--> CARGADA --abono ok--> ABONADA --asientos--> COMPLETADA (y se publica el evento)
 *      |                     |
 *      | cargo rechazado     | abono fallo: se devuelve el cargo
 *      v                     v
 *   FALLIDA          devolucion ok --> COMPENSADA
 *                    devolucion fallo --> COMPENSACION_PENDIENTE --reintento programado ok--> COMPENSADA
 * </pre>
 *
 * El abono es el punto sin retorno (el "pivote"): antes de el, si algo falla, se compensa; despues
 * de el el dinero ya llego y lo que falta (los asientos) se termina, no se deshace. Desde v09 el aviso
 * al cliente no es un paso de la saga: se publica como evento cuando la saga ya termino.
 */
public enum EstadoTransferencia {
	/** Hay fila, pero todavia no se toco ningun saldo. */
	INICIADA,
	/** El dinero salio de origen y no ha llegado a destino. Estado intermedio: nunca deberia quedarse asi. */
	CARGADA,
	/** El dinero llego a destino. Faltan los asientos del ledger. */
	ABONADA,
	/** Final feliz: saldos y asientos. Despues de guardarla se publica TransferenciaRealizada (v09). */
	COMPLETADA,
	/** Final: el cargo no se pudo hacer (saldo insuficiente, cuenta caida). No se movio dinero. */
	FALLIDA,
	/** Final: el abono fallo y el cargo se devolvio a origen, con su asiento en el ledger. Se publica TransferenciaRevertida (v09). */
	COMPENSADA,
	/** El abono fallo y la devolucion tambien. El dinero sigue fuera de origen: el reintento programado la termina. */
	COMPENSACION_PENDIENTE
}
