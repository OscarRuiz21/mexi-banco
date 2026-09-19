package mx.mexibanco.movimiento;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Estado de cuenta: GET /movimientos?clabe=... , del mas reciente al mas viejo.
 * Va bajo /movimientos y no bajo /cuentas/{clabe}/... para que cuando esto sea un servicio aparte
 * el gateway lo enrute por prefijo sin pasar por cuentas.
 */
@RestController
@RequestMapping("/movimientos")
public class MovimientoController {

	private final MovimientoService movimientos;

	public MovimientoController(MovimientoService movimientos) {
		this.movimientos = movimientos;
	}

	@GetMapping
	public List<Movimiento> estadoDeCuenta(@RequestParam String clabe) {
		return movimientos.historial(clabe);
	}
}
