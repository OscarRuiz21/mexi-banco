package mx.mexibanco.cuenta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {
	Optional<Cuenta> findByClabe(String clabe);

	boolean existsByClabe(String clabe);
}
