package mx.mexibanco.clientes;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por HTTP a CUENTA_URL
 * (en v06, http://cuenta: el nombre en el directorio, y una replica distinta en cada peticion).
 * Cada metodo es una peticion independiente que se confirma sola en el servicio cuenta:
 * no hay transaccion que envuelva un cargo y el abono que le sigue (eso lo arregla la saga de
 * TransferenciaService, no este cliente).
 *
 * v08, dos cosas nuevas:
 *
 * 1. CIRCUIT BREAKER (Resilience4j). Toda llamada a cuenta pasa por el circuito "cuenta". Cerrado
 *    (CLOSED): las llamadas pasan y se cuentan. Si en las ultimas N llamadas falla la mitad o mas,
 *    se abre (OPEN): durante unos segundos NO se llama a cuenta, se falla de inmediato. Pasado ese
 *    tiempo queda semiabierto (HALF_OPEN): deja pasar unas pocas de prueba; si salen bien se cierra,
 *    si fallan se vuelve a abrir. Los umbrales estan en application.yml (resilience4j.circuitbreaker).
 *    Un 404 o un 422 de cuenta NO cuentan como falla: cuenta contesto bien que no (ver FallaDeCuenta).
 *
 *    Se usa el registro de Resilience4j directo (circuito.executeSupplier) y no el
 *    CircuitBreakerFactory.run(...) de Spring Cloud porque ese, sin fallback, envuelve cada error en
 *    NoFallbackAvailableException (el 404 de cuenta saldria como 500) y ademas corre la llamada en
 *    otro hilo con su propio limite de tiempo. Aqui el timeout es el del RestClient (ClientesHttp).
 *
 * 2. IDEMPOTENCY-KEY en cargos y abonos. Quien llama dice que paso es (transferencia-12-devolucion)
 *    y cuenta guarda esa clave junto con el cambio de saldo: si llega otra vez, no lo aplica de nuevo.
 */
@Component
public class CuentaCliente {

	/** El nombre del circuito: el mismo que en application.yml y en /actuator/circuitbreakers. */
	public static final String CIRCUITO = "cuenta";

	private static final Logger log = LoggerFactory.getLogger(CuentaCliente.class);

	private final RestClient http;
	private final CircuitBreaker circuito;

	public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase,
						 CircuitBreakerRegistry circuitos) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
		this.circuito = circuitos.circuitBreaker(CIRCUITO);
		// Para verlo en clase con docker compose logs -f transferencia: CLOSED -> OPEN -> HALF_OPEN -> CLOSED
		this.circuito.getEventPublisher().onStateTransition(evento ->
			log.warn("Circuito hacia cuenta: {}", evento.getStateTransition()));
	}

	public CuentaRemota consultar(String clabe) {
		return protegida(() -> http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto, String clave) {
		return protegida(() -> http.post().uri("/cuentas/{clabe}/cargos", clabe)
			.header(ClientesHttp.IDEMPOTENCY_KEY, clave)
			.body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto, String clave) {
		return protegida(() -> http.post().uri("/cuentas/{clabe}/abonos", clabe)
			.header(ClientesHttp.IDEMPOTENCY_KEY, clave)
			.body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class));
	}

	/**
	 * La llamada pasa por el circuito. Con el circuito abierto, Resilience4j ni siquiera la intenta y
	 * lanza CallNotPermittedException; se traduce a la misma excepcion de siempre (503 de cuenta) para
	 * que el resto del servicio no tenga que saber que existe un breaker.
	 */
	private <T> T protegida(Supplier<T> llamada) {
		try {
			return circuito.executeSupplier(llamada);
		} catch (CallNotPermittedException abierto) {
			throw new ErrorRemotoException("cuenta", 503,
				"Circuito abierto hacia cuenta: fallo varias veces seguidas y no se le llama por unos segundos");
		}
	}
}
