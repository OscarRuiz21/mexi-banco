package mx.mexibanco.transferencia;

/**
 * En que paso de la saga va una transferencia (v08). Se guarda en la base en CADA paso: si el
 * servicio se cae a la mitad, la fila dice hasta donde llego y que falta.
 *
 * <pre>
 *   INICIADA --cargo ok--> CARGADA --abono ok--> ABONADA --asientos y avisos--> COMPLETADA
 *      |                     |
 *      | cargo rechazado     | abono fallo: se devuelve el cargo
 *      v                     v
 *   FALLIDA          devolucion ok --> COMPENSADA
 *                    devolucion fallo --> COMPENSACION_PENDIENTE --reintento programado ok--> COMPENSADA
 * </pre>
 *
 * El abono es el punto sin retorno (el "pivote"): antes de el, si algo falla, se compensa; despues
 * de el el dinero ya llego y lo que falta (asientos, avisos) se termina, no se deshace.
 */
public enum EstadoTransferencia {
	/** Hay fila, pero todavia no se toco ningun saldo. */
	INICIADA,
	/** El dinero salio de origen y no ha llegado a destino. Estado intermedio: nunca deberia quedarse asi. */
	CARGADA,
	/** El dinero llego a destino. Faltan los asientos del ledger y los avisos. */
	ABONADA,
	/** Final feliz: saldos, asientos y avisos. */
	COMPLETADA,
	/** Final: el cargo no se pudo hacer (saldo insuficiente, cuenta caida). No se movio dinero. */
	FALLIDA,
	/** Final: el abono fallo y el cargo se devolvio a origen, con su asiento en el ledger. */
	COMPENSADA,
	/** El abono fallo y la devolucion tambien. El dinero sigue fuera de origen: el reintento programado la termina. */
	COMPENSACION_PENDIENTE
}
