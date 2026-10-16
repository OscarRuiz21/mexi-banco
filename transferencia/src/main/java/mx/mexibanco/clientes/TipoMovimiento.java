package mx.mexibanco.clientes;

/**
 * Copia del enum del servicio movimiento. Si movimiento agrega un tipo, esta copia no se entera
 * sola: es el precio de no compartir codigo entre servicios (ver README). En v08 se agrego
 * DEVOLUCION_TRANSFERENCIA en los dos lados, a mano.
 */
public enum TipoMovimiento {
	DEPOSITO,
	TRANSFERENCIA_ENVIADA,
	TRANSFERENCIA_RECIBIDA,
	SPEI_ENVIADO,
	/** v08: la compensacion de la saga, el cargo de una transferencia que regresa a origen. */
	DEVOLUCION_TRANSFERENCIA
}
