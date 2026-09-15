package mx.mexibanco.cuenta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/cuentas")
public class CuentaController {

	private final CuentaRepository cuentas;

	public CuentaController(CuentaRepository cuentas) {
		this.cuentas = cuentas;
	}

	public record AltaCuenta(@NotBlank String clabe, @NotBlank String titular, @PositiveOrZero BigDecimal saldoInicial) {
	}

	@PostMapping
	public ResponseEntity<Cuenta> abrir(@RequestBody AltaCuenta datos) {
		Cuenta cuenta = new Cuenta(datos.clabe(), datos.titular(), datos.saldoInicial());
		return ResponseEntity.status(HttpStatus.CREATED).body(cuentas.save(cuenta));
	}

	@GetMapping("/{clabe}")
	public Cuenta consultar(@PathVariable String clabe) {
		return cuentas.findByClabe(clabe)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la CLABE " + clabe));
	}
}
