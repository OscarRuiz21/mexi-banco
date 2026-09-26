package mx.mexibanco.compartido;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * Unico lugar donde una excepcion se vuelve respuesta HTTP (RFC 9457, Problem Details).
 * Gracias a esto ningun servicio de negocio importa nada de Spring Web.
 *
 * Esta clase es identica en los cinco servicios: se copio, no se comparte (ver README).
 */
@RestControllerAdvice
public class ManejadorDeErrores {

	@ExceptionHandler(NoEncontradoException.class)
	ProblemDetail noEncontrado(NoEncontradoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	ProblemDetail conflicto(ConflictoException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	@ExceptionHandler(ReglaDeNegocioException.class)
	ProblemDetail reglaDeNegocio(ReglaDeNegocioException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
	}

	/** Dos escrituras al mismo saldo al mismo tiempo: gana una, la otra se reintenta (@Version en Cuenta). */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail escrituraConcurrente(OptimisticLockingFailureException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Otra operacion modifico el mismo registro; reintenta");
	}

	/** El otro servicio contesto con error: se devuelve el mismo codigo y se dice quien fue. */
	@ExceptionHandler(ErrorRemotoException.class)
	ProblemDetail errorRemoto(ErrorRemotoException e) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(e.getEstado()), e.getMessage());
		problema.setProperty("servicio", e.getServicio());
		return problema;
	}

	/** El otro servicio ni siquiera contesto (apagado, nombre que no resuelve, timeout). */
	@ExceptionHandler(ResourceAccessException.class)
	ProblemDetail servicioNoDisponible(ResourceAccessException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
			"No se pudo contactar a otro servicio: " + e.getMessage());
	}
}
