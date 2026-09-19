package mx.mexibanco.movimiento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * El ledger. Solo sabe agregar asientos y leerlos: no hay metodo para editar ni borrar, a proposito.
 * No depende de ningun otro modulo; los demas le dictan el asiento ya calculado.
 */
@Service
public class MovimientoService {

	private final MovimientoRepository movimientos;

	public MovimientoService(MovimientoRepository movimientos) {
		this.movimientos = movimientos;
	}

	@Transactional
	public Movimiento registrar(String clabeCuenta, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldoResultante, String referencia) {
		return movimientos.save(new Movimiento(clabeCuenta, tipo, monto, saldoResultante, referencia));
	}

	@Transactional(readOnly = true)
	public List<Movimiento> historial(String clabeCuenta) {
		return movimientos.findByClabeCuentaOrderByFechaDesc(clabeCuenta);
	}
}
