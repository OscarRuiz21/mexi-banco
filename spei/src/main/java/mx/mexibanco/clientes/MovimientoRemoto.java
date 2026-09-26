package mx.mexibanco.clientes;

import java.math.BigDecimal;
import java.time.Instant;

/** Lo que el servicio movimiento devuelve en JSON al registrar un asiento. Copia local. */
public record MovimientoRemoto(Long id, String clabeCuenta, TipoMovimiento tipo, BigDecimal monto,
							   BigDecimal saldoResultante, Instant fecha, String referencia) {
}
