package mx.mexibanco;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * El directorio de Mexi Banco v06. Cada servicio se da de alta aqui con su nombre logico
 * (cuenta, movimiento, ...) y su IP, y manda un latido cada tanto. Quien quiere hablar con
 * "cuenta" pregunta aqui que instancias hay, en vez de tener escrita una URL.
 *
 * Dashboard: http://localhost:8761
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryApplication {

	public static void main(String[] args) {
		SpringApplication.run(DiscoveryApplication.class, args);
	}
}
