package mx.mexibanco.mensajeria;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EventoPendienteRepository extends JpaRepository<EventoPendiente, UUID> {

	/**
	 * Las filas pendientes mas viejas, BLOQUEADAS para esta transaccion (FOR UPDATE) y saltando las
	 * que ya bloqueo otro (SKIP LOCKED). Si hubiera dos replicas de transferencia, cada relevo se
	 * lleva filas distintas en lugar de publicar las mismas dos veces o esperarse uno al otro.
	 * Solo funciona dentro de una transaccion (ver RelevoDelOutbox).
	 */
	@Query(value = """
		SELECT * FROM outbox
		WHERE publicado IS NULL
		ORDER BY creado
		LIMIT :lote
		FOR UPDATE SKIP LOCKED
		""", nativeQuery = true)
	List<EventoPendiente> tomarPendientes(@Param("lote") int lote);
}
