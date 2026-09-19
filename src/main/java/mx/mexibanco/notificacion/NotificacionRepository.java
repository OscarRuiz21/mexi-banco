package mx.mexibanco.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
	List<Notificacion> findByClabeCuentaOrderByFechaDesc(String clabeCuenta);
}
