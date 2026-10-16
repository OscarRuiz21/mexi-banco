package mx.mexibanco.cuenta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OperacionAplicadaRepository extends JpaRepository<OperacionAplicada, Long> {
	Optional<OperacionAplicada> findByClave(String clave);
}
