package mx.mexibanco.spei;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Quinto componente de Mexi Banco: SPEI, transferencia externa a otro banco.
 * Requiere la cabecera Idempotency-Key, igual que en la T01.
 */
@RestController
@RequestMapping("/spei")
public class SpeiController {

	private final SpeiService speiService;

	public SpeiController(SpeiService speiService) {
		this.speiService = speiService;
	}

	@PostMapping
	public ResponseEntity<SpeiSolicitud> enviar(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody SpeiService.SolicitudSpei datos
	) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta la cabecera Idempotency-Key");
		}
		SpeiSolicitud resultado = speiService.procesar(idempotencyKey, datos);
		return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
	}
}
