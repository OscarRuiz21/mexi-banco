package mx.mexibanco.clientes;

/**
 * Copia del enum del servicio movimiento. Si movimiento agrega un tipo, esta copia no se entera
 * sola: es el precio de no compartir codigo entre servicios (ver README).
 */
public enum TipoMovimiento {
	DEPOSITO,
	TRANSFERENCIA_ENVIADA,
	TRANSFERENCIA_RECIBIDA,
	SPEI_ENVIADO
}
