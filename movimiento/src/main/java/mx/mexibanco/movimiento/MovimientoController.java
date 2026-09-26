package mx.mexibanco.movimiento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /movimientos?clabe=... es el estado de cuenta publico, del mas reciente al mas viejo.
 * POST /movimientos es NUEVO en v06a y es interno: en el monolito cuenta, transferencia y SPEI
 * llamaban a MovimientoService.registrar(...) como metodo; ahora lo piden por HTTP.
 */
@RestController
@RequestMapping("/movimientos")
public class MovimientoController {

	private final MovimientoService movimientos;

	public MovimientoController(MovimientoService movimientos) {
		this.movimientos = movimientos;
	}

	public record NuevoAsiento(@NotBlank String clabeCuenta, @NotNull TipoMovimiento tipo, @NotNull BigDecimal monto,
							   @NotNull BigDecimal saldoResultante, String referencia) {
	}

	@GetMapping
	public List<Movimiento> estadoDeCuenta(@RequestParam String clabe) {
		return movimientos.historial(clabe);
	}

	/** Interno: lo usan cuenta, transferencia y spei. El monto llega con signo (negativo = cargo). */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Movimiento registrar(@Valid @RequestBody NuevoAsiento asiento) {
		return movimientos.registrar(asiento.clabeCuenta(), asiento.tipo(), asiento.monto(),
			asiento.saldoResultante(), asiento.referencia());
	}
}
