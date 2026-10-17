package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, UUID> {

	/**
	 * Intenta poner la marca y dice si la puso: 1 si el evento es nuevo, 0 si ya estaba.
	 *
	 * Es UNA sentencia de Postgres (INSERT ... ON CONFLICT DO NOTHING) y no "consultar si existe y
	 * luego insertar": entre esas dos llamadas otra entrega del mismo mensaje podria colarse. Aqui la
	 * llave primaria decide; si dos llegan a la vez, la segunda espera a la primera y luego no inserta.
	 * Tampoco se atrapa una violacion de llave: en Postgres eso deja la transaccion abortada.
	 */
	@Modifying
	@Query(value = """
		INSERT INTO evento_procesado (evento_id, tipo, transferencia_id, procesado)
		VALUES (:eventoId, :tipo, :transferenciaId, now())
		ON CONFLICT (evento_id) DO NOTHING
		""", nativeQuery = true)
	int marcarSiEsNuevo(@Param("eventoId") UUID eventoId, @Param("tipo") String tipo,
						@Param("transferenciaId") Long transferenciaId);
}
