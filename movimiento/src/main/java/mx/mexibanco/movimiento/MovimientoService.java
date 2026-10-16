package mx.mexibanco.movimiento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * El ledger. Solo sabe agregar asientos y leerlos: no hay metodo para editar ni borrar, a proposito.
 * No depende de ningun otro servicio; los demas le dictan el asiento ya calculado por HTTP
 * (POST /movimientos). Es el unico servicio que abre la base "movimiento".
 */
@Service
public class MovimientoService {

	private final MovimientoRepository movimientos;

	public MovimientoService(MovimientoRepository movimientos) {
		this.movimientos = movimientos;
	}

	/**
	 * v08: clave es la Idempotency-Key (opcional). Si ya hay un asiento con esa clave, se regresa ese
	 * mismo y no se guarda otro: asi la compensacion de transferencia se puede reintentar sin duplicar
	 * el estado de cuenta. Si dos llegan a la vez, el UNIQUE de la columna deja entrar a uno solo.
	 */
	@Transactional
	public Movimiento registrar(String clave, String clabeCuenta, TipoMovimiento tipo, BigDecimal monto,
								BigDecimal saldoResultante, String referencia) {
		String claveONula = clave == null || clave.isBlank() ? null : clave;
		if (claveONula != null) {
			Optional<Movimiento> previo = movimientos.findByClave(claveONula);
			if (previo.isPresent()) {
				return previo.get();
			}
		}
		return movimientos.save(new Movimiento(claveONula, clabeCuenta, tipo, monto, saldoResultante, referencia));
	}

	@Transactional(readOnly = true)
	public List<Movimiento> historial(String clabeCuenta) {
		return movimientos.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
