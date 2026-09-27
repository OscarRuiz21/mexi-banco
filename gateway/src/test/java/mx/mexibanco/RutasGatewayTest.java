package mx.mexibanco;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las rutas del gateway, sin Eureka ni servicios: el directorio esta apagado, asi que una ruta que
 * existe responde 503 ("no hay instancias de cuenta") y una que no existe responde 404.
 * Esa diferencia basta para saber que peticion tiene puerta y cual no.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = "eureka.client.enabled=false")
@AutoConfigureWebTestClient
class RutasGatewayTest {

	@Autowired
	RouteLocator rutas;

	@Autowired
	WebTestClient cliente;

	// TODO H2 (comprobacion): cuando termines las rutas, borra @Disabled y corre ./mvnw test en gateway.
	@Disabled("Pasa cuando esten las cinco rutas (H2)")
	@Test
	void hayUnaRutaPorServicioYTodasVanPorElDirectorio() {
		List<Route> todas = rutas.getRoutes().collectList().block();

		assertThat(todas).extracting(Route::getId)
			.containsExactlyInAnyOrder("cuenta", "movimiento", "transferencia", "notificacion", "spei");
		assertThat(todas).allSatisfy(ruta -> assertThat(ruta.getUri().getScheme()).isEqualTo("lb"));
		assertThat(todas).allSatisfy(ruta -> assertThat(ruta.getUri().getHost()).isEqualTo(ruta.getId()));
	}

	// TODO H2 (comprobacion): cuando termines las rutas, borra @Disabled y corre ./mvnw test en gateway.
	@Disabled("Pasa cuando esten las cinco rutas (H2)")
	@Test
	void losEndpointsPublicosTienenPuerta() {
		cliente.get().uri("/api/cuentas/002180000000000001").exchange().expectStatus().isEqualTo(503);
		cliente.post().uri("/api/cuentas").exchange().expectStatus().isEqualTo(503);
		cliente.get().uri("/api/movimientos?clabe=1").exchange().expectStatus().isEqualTo(503);
		cliente.post().uri("/api/transferencias").exchange().expectStatus().isEqualTo(503);
		cliente.get().uri("/api/transferencias/1").exchange().expectStatus().isEqualTo(503);
		cliente.get().uri("/api/notificaciones?clabe=1").exchange().expectStatus().isEqualTo(503);
		cliente.post().uri("/api/spei").exchange().expectStatus().isEqualTo(503);
	}

	@Test
	void losEndpointsInternosNoTienenPuerta() {
		cliente.post().uri("/api/cuentas/002180000000000001/abonos").exchange().expectStatus().isNotFound();
		cliente.post().uri("/api/cuentas/002180000000000001/cargos").exchange().expectStatus().isNotFound();
		cliente.post().uri("/api/movimientos").exchange().expectStatus().isNotFound();
		cliente.post().uri("/api/notificaciones").exchange().expectStatus().isNotFound();
		cliente.get().uri("/cuentas/002180000000000001").exchange().expectStatus().isNotFound();
	}
}
