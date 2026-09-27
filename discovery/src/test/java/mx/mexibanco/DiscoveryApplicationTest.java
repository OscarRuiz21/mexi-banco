package mx.mexibanco;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El directorio arranca solo (sin base ni otros servicios) y responde su API REST de registro.
 * Levanta el servidor en un puerto al azar: no choca con un Eureka que ya este corriendo.
 */
// TODO H1 (comprobacion): cuando termines H1, borra @Disabled y corre ./mvnw test en discovery.
// Va sobre la clase y no sobre el metodo porque Spring levanta el contexto al crear la clase de
// prueba, y sin @EnableEurekaServer el contexto ni siquiera arranca.
@Disabled("Pasa cuando discovery ya es servidor Eureka (H1)")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryApplicationTest {

	@Autowired
	TestRestTemplate http;

	@Test
	void elDirectorioArrancaVacioYRespondeSuRegistro() {
		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.setAccept(List.of(MediaType.APPLICATION_JSON));
		ResponseEntity<String> respuesta = http.exchange("/eureka/apps", HttpMethod.GET, new HttpEntity<>(cabeceras), String.class);

		assertThat(respuesta.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(respuesta.getBody()).contains("\"applications\"");
	}
}
