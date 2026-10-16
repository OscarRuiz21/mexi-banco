package mx.mexibanco.transferencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

	/** Lo usa el reintento programado (COMPENSACION_PENDIENTE) y GET /transferencias?estado=... */
	List<Transferencia> findByEstado(EstadoTransferencia estado);
}
