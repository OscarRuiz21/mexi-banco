package mx.mexibanco.movimiento;

public enum TipoMovimiento {
	DEPOSITO,
	TRANSFERENCIA_ENVIADA,
	TRANSFERENCIA_RECIBIDA,
	SPEI_ENVIADO,
	/** v08: la compensacion de la saga de transferencia, el dinero que regresa a origen porque el abono fallo. */
	DEVOLUCION_TRANSFERENCIA
}
