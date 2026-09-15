package mx.mexibanco.spei;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpeiSolicitudRepository extends JpaRepository<SpeiSolicitud, Long> {
	Optional<SpeiSolicitud> findByIdempotencyKey(String idempotencyKey);
}
