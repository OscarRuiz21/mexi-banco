package mx.mexibanco.clientes;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Nueva en v08: el circuit breaker de CuentaCliente con la configuracion REAL de application.yml
 * (ventana de 4, 50%, que cuenta como falla). Cuenta es un servidor HTTP minimo en esta maquina al
 * que se le cambia el humor: sano, caido (503), "no existe" (404) o lento. Sin Docker ni base.
 * Solo se acorta el tiempo en abierto (1 s en lugar de 10) y el timeout de lectura, para no esperar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
	classes = CircuitoHaciaCuentaTest.Contexto.class,
	properties = {"eureka.client.enabled=false", "mexibanco.servicios.cuenta=http://cuenta",
		"resilience4j.circuitbreaker.instances.cuenta.wait-duration-in-open-state=1s",
		"mexibanco.timeouts.lectura-ms=300"})
class CircuitoHaciaCuentaTest {

	@Configuration
	@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
	@Import({ClientesHttp.class, CuentaCliente.class})
	static class Contexto {
	}

	enum Humor { SANO, CAIDO, NO_EXISTE, LENTO }

	private static final AtomicReference<Humor> HUMOR = new AtomicReference<>(Humor.SANO);
	private static final AtomicInteger PETICIONES = new AtomicInteger();
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
	CuentaCliente cuentas;

	@Autowired
	CircuitBreakerRegistry circuitos;

	CircuitBreaker circuito;

	@BeforeEach
	void cerrarElCircuito() {
		circuito = circuitos.circuitBreaker(CuentaCliente.CIRCUITO);
		circuito.reset();
		HUMOR.set(Humor.SANO);
		PETICIONES.set(0);
	}

	@Test
	void cuatroFallasSeguidasAbrenElCircuitoYLaQuintaYaNoLlegaACuenta() {
		HUMOR.set(Humor.CAIDO);
		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> cuentas.consultar("1")).isInstanceOf(ErrorRemotoException.class);
		}
		assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		assertThatThrownBy(() -> cuentas.consultar("1"))
			.isInstanceOfSatisfying(ErrorRemotoException.class, e -> {
				assertThat(e.getEstado()).isEqualTo(503);
				assertThat(e.getMessage()).contains("Circuito abierto");
			});
		assertThat(PETICIONES.get()).as("con el circuito abierto no se llama a cuenta").isEqualTo(4);
	}

	@Test
	void los404DeCuentaNoAbrenElCircuitoPorqueCuentaContestoBien() {
		HUMOR.set(Humor.NO_EXISTE);
		for (int i = 0; i < 6; i++) {
			assertThatThrownBy(() -> cuentas.consultar("1"))
				.isInstanceOfSatisfying(ErrorRemotoException.class, e -> assertThat(e.getEstado()).isEqualTo(404));
		}
		assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void abiertoPasaSoloASemiabiertoYDosLlamadasBuenasLoCierran() {
		HUMOR.set(Humor.CAIDO);
		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> cuentas.consultar("1")).isInstanceOf(ErrorRemotoException.class);
		}
		assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

		await().atMost(Duration.ofSeconds(5)).until(() -> circuito.getState() == CircuitBreaker.State.HALF_OPEN);
		HUMOR.set(Humor.SANO);
		cuentas.consultar("1");
		cuentas.consultar("1");

		assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void unaCuentaLentaSeCortaConElTimeoutYCuentaComoFalla() {
		HUMOR.set(Humor.LENTO);
		long inicio = System.nanoTime();

		assertThatThrownBy(() -> cuentas.consultar("1")).isInstanceOf(ResourceAccessException.class);

		assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(1500));
		assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
	}

	/** GET /cuentas/{clabe} con el humor del momento. Lento = 3 s antes de contestar. */
	private static HttpServer cuentaFalsa() {
		try {
			HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			servidor.createContext("/cuentas/", intercambio -> {
				PETICIONES.incrementAndGet();
				Humor humor = HUMOR.get();
				int codigo = switch (humor) {
					case SANO, LENTO -> 200;
					case CAIDO -> 503;
					case NO_EXISTE -> 404;
				};
				String json = switch (humor) {
					case SANO, LENTO -> "{\"clabe\":\"1\",\"titular\":\"Ana\",\"saldo\":1000}";
					case CAIDO -> "{\"status\":503,\"detail\":\"cuenta en mantenimiento\"}";
					case NO_EXISTE -> "{\"status\":404,\"detail\":\"No existe la CLABE 1\"}";
				};
				if (humor == Humor.LENTO) {
					try {
						Thread.sleep(3_000);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
				intercambio.getResponseHeaders().add("Content-Type", "application/json");
				try {
					intercambio.sendResponseHeaders(codigo, cuerpo.length);
					try (OutputStream salida = intercambio.getResponseBody()) {
						salida.write(cuerpo);
					}
				} catch (IOException clienteQueYaColgo) {
					// el cliente corto por timeout: nada que hacer
				}
			});
			servidor.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
			servidor.start();
			return servidor;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
