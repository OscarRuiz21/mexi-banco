package mx.mexibanco.clientes;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nueva en v06: CuentaCliente habla con http://cuenta (un nombre, sin puerto) y el RestClient
 * balanceado reparte entre las instancias que conoce el directorio. Aqui el directorio es la lista
 * fija de Spring Cloud (dos "replicas" de cuenta en puertos al azar de esta maquina) en lugar de
 * Eureka: sin Docker, sin base y sin red externa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
	classes = BalanceoPorNombreTest.Contexto.class,
	properties = {"eureka.client.enabled=false", "mexibanco.servicios.cuenta=http://cuenta"})
class BalanceoPorNombreTest {

	@Configuration
	@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
	@Import({ClientesHttp.class, CuentaCliente.class})
	static class Contexto {
	}

	private static final HttpServer REPLICA_A = replica("replica-a");
	private static final HttpServer REPLICA_B = replica("replica-b");

	@DynamicPropertySource
	static void directorio(DynamicPropertyRegistry registro) {
		registro.add("spring.cloud.discovery.client.simple.instances.cuenta[0].uri",
			() -> "http://localhost:" + REPLICA_A.getAddress().getPort());
		registro.add("spring.cloud.discovery.client.simple.instances.cuenta[1].uri",
			() -> "http://localhost:" + REPLICA_B.getAddress().getPort());
	}

	@AfterAll
	static void apagar() {
		REPLICA_A.stop(0);
		REPLICA_B.stop(0);
	}

	@Autowired
	CuentaCliente cuentas;

	// TODO H3 (comprobacion): cuando declares el builder balanceado, borra @Disabled y corre
	// ./mvnw test en transferencia. Sin @LoadBalanced falla con "I/O error ... http://cuenta/...".
	@Disabled("Pasa cuando ClientesHttp tenga el RestClient.Builder con @LoadBalanced (H3)")
	@Test
	void llamarPorNombreRepartePeticionesEntreLasReplicasEnRoundRobin() {
		List<String> quienRespondio = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			quienRespondio.add(cuentas.consultar("002180000000000001").titular());
		}

		assertThat(new HashSet<>(quienRespondio)).containsExactlyInAnyOrder("replica-a", "replica-b");
		for (int i = 1; i < quienRespondio.size(); i++) {
			assertThat(quienRespondio.get(i)).as("round robin: nunca la misma dos veces seguidas")
				.isNotEqualTo(quienRespondio.get(i - 1));
		}
	}

	/** Un servidor HTTP minimo que contesta GET /cuentas/{clabe} con su propio nombre como titular. */
	private static HttpServer replica(String nombre) {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			servidor.createContext("/cuentas/", intercambio -> {
				byte[] cuerpo = ("{\"clabe\":\"002180000000000001\",\"titular\":\"" + nombre + "\",\"saldo\":1000}")
					.getBytes(StandardCharsets.UTF_8);
				intercambio.getResponseHeaders().add("Content-Type", "application/json");
				intercambio.sendResponseHeaders(200, cuerpo.length);
				try (OutputStream salida = intercambio.getResponseBody()) {
					salida.write(cuerpo);
				}
			});
			servidor.start();
			return servidor;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
