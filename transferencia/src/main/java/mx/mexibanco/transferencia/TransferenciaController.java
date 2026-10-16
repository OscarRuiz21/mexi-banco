package mx.mexibanco.transferencia;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/transferencias")
public class TransferenciaController {

	private final TransferenciaService transferencias;

	public TransferenciaController(TransferenciaService transferencias) {
		this.transferencias = transferencias;
	}

	public record SolicitudTransferencia(@NotBlank String claveOrigen, @NotBlank String claveDestino, @NotNull @Positive BigDecimal monto) {
	}

	/**
	 * 201 con la transferencia si termino COMPLETADA. Si la saga tuvo que compensar, 502 con un
	 * Problem Details que dice que paso: que el abono fallo, si el dinero ya regreso a origen
	 * (COMPENSADA) o si se esta reintentando la devolucion (COMPENSACION_PENDIENTE), y el id para
	 * consultarla despues con GET /transferencias/{id}. 502 porque fallo un servicio del que este
	 * depende, no la peticion del cliente.
	 */
	@PostMapping
	public ResponseEntity<?> transferir(@Valid @RequestBody SolicitudTransferencia solicitud) {
		Transferencia t = transferencias.transferir(solicitud.claveOrigen(), solicitud.claveDestino(), solicitud.monto());
		return switch (t.getEstado()) {
			case COMPLETADA -> ResponseEntity.status(HttpStatus.CREATED).body(t);
			case COMPENSADA, COMPENSACION_PENDIENTE -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(noRealizada(t));
			case INICIADA, CARGADA, ABONADA, FALLIDA ->
				throw new IllegalStateException("La saga no deberia regresar una transferencia en " + t.getEstado());
		};
	}

	@GetMapping("/{id}")
	public Transferencia consultar(@PathVariable Long id) {
		return transferencias.consultar(id);
	}

	/** v08: GET /transferencias?estado=COMPENSACION_PENDIENTE, para ver en clase que hay atorado. */
	@GetMapping
	public List<Transferencia> buscar(@RequestParam EstadoTransferencia estado) {
		return transferencias.buscarPorEstado(estado);
	}

	private static ProblemDetail noRealizada(Transferencia t) {
		String detalle = t.getEstado() == EstadoTransferencia.COMPENSADA
			? "No se pudo abonar a la CLABE %s, asi que se devolvieron %s a la CLABE %s: su saldo quedo como estaba."
			.formatted(t.getClaveDestino(), t.getMonto(), t.getClaveOrigen())
			: ("No se pudo abonar a la CLABE %s y la devolucion de %s a la CLABE %s tampoco pudo hacerse todavia. "
			+ "El dinero salio de origen; el banco reintentara la devolucion automaticamente.")
			.formatted(t.getClaveDestino(), t.getMonto(), t.getClaveOrigen());
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, detalle);
		problema.setTitle("Transferencia no realizada");
		problema.setProperty("transferencia", t.getId());
		problema.setProperty("estado", t.getEstado());
		problema.setProperty("motivo", t.getMotivo());
		return problema;
	}
}
