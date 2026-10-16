package mx.mexibanco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servicio transferencia de Mexi Banco: un proceso, un contenedor, su propia base.
 * v08: @EnableScheduling enciende el reintento programado de la saga (ReintentoDeCompensaciones).
 */
@SpringBootApplication
@EnableScheduling
public class TransferenciaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TransferenciaApplication.class, args);
	}
}
