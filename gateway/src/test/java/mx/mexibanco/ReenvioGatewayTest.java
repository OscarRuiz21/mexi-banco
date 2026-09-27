package mx.mexibanco;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * El gateway de verdad reenvia: una "replica" de cuenta falsa (un servidor HTTP en un puerto al
 * azar) registrada en la lista fija de Spring Cloud, en lugar de Eureka. Comprueba que la peticion
 * llega sin /api, que la respuesta y sus cabeceras regresan intactas.
 *
 * Esta prueba existe porque con Spring Cloud 2023.0.4 en adelante y Boot 3.3.4 el gateway compila y
 * arranca, pero truena con NoSuchMethodError al reenviar la primera peticion (ver README).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@AutoConfigureWebTestClient
class ReenvioGatewayTest {

	private static final AtomicReference<String> RUTA_RECIBIDA = new AtomicReference<>();
	private static final HttpServer CUENTA = cuentaFalsa();

	@DynamicPropertySource
	static void directorio(DynamicPropertyRegistry registro) {
		registro.add("spring.cloud.discovery.client.simple.instances.cuenta[0].uri",
			() -> "http://localhost:" + CUENTA.getAddress().getPort());
	}

	@AfterAll
	static void apagar() {
		CUENTA.stop(0);
	}

	@Autowired
	WebTestClient cliente;

	@Test
	void unaPeticionPorLaPuertaLlegaACuentaSinElPrefijoYRegresaIntacta() {
		cliente.get().uri("/api/cuentas/002180000000000001").exchange()
			.expectStatus().isOk()
			.expectHeader().valueEquals("X-Instancia", "replica-falsa")
			.expectBody().jsonPath("$.titular").isEqualTo("Ana");

		org.assertj.core.api.Assertions.assertThat(RUTA_RECIBIDA.get()).isEqualTo("/cuentas/002180000000000001");
	}

	private static HttpServer cuentaFalsa() {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			servidor.createContext("/", intercambio -> {
				RUTA_RECIBIDA.set(intercambio.getRequestURI().getPath());
				byte[] cuerpo = "{\"clabe\":\"002180000000000001\",\"titular\":\"Ana\",\"saldo\":1000}".getBytes(StandardCharsets.UTF_8);
				intercambio.getResponseHeaders().add("Content-Type", "application/json");
				intercambio.getResponseHeaders().add("X-Instancia", "replica-falsa");
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
