package mx.mexibanco.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
	List<Movimiento> findByClabeCuentaOrderByFechaDesc(String clabeCuenta);

	Optional<Movimiento> findByClave(String clave);
}
