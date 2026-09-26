package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;

	public TransferenciaController(TransferenciaService transferencias) {
		this.transferencias = transferencias;
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen, @NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) {
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Transferencia transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		return transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}
}
