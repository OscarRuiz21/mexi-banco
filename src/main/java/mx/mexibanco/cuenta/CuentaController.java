package mx.mexibanco.cuenta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** Capa web: traduce HTTP a llamadas al servicio y nada mas. Sin reglas de negocio aqui. */
@RestController
@RequestMapping("/cuentas")
public class CuentaController {

	private final CuentaService cuentas;

	public CuentaController(CuentaService cuentas) {
		this.cuentas = cuentas;
	}

	public record AltaCuenta(@NotBlank String clabe, @NotBlank String titular, @NotNull @PositiveOrZero BigDecimal saldoInicial) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Cuenta abrir(@Valid @RequestBody AltaCuenta datos) {
		return cuentas.abrir(datos.clabe(), datos.titular(), datos.saldoInicial());
	}

	@GetMapping("/{clabe}")
	public Cuenta consultar(@PathVariable String clabe) {
		return cuentas.consultar(clabe);
	}
}
