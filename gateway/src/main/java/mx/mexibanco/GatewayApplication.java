package mx.mexibanco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * La puerta de Mexi Banco v06. El cliente solo conoce http://localhost:8080/api/...; el gateway
 * decide a que servicio va cada peticion (rutas en application.yml) y, con lb://, a cual de sus
 * instancias, preguntandole al directorio (Eureka). No tiene logica de negocio ni base de datos.
 */
@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}
}
