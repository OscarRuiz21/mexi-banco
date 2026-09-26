package mx.mexibanco.movimiento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
	List<Movimiento> findByClabeCuentaOrderByFechaDesc(String clabeCuenta);
}
