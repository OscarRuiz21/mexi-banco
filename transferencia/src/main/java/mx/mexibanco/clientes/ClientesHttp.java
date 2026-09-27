package mx.mexibanco.clientes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Como se habla con otro servicio en v06: RestClient contra el NOMBRE del servicio
 * (http://cuenta, sin puerto), no contra una direccion. El RestClient.Builder de abajo lleva
 * @LoadBalanced: antes de cada peticion le pregunta al directorio (Eureka) que instancias de
 * "cuenta" hay y escoge una en round robin. Si cuenta tiene tres replicas, el cargo puede ir a una
 * y el abono a otra.
 *
 * En v06a esta clase estaba copiada igual en cuenta, transferencia y spei; en v06 solo la de
 * transferencia tiene el builder balanceado (ver README).
 */
@Configuration
public class ClientesHttp {

	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Tiempos maximos para cualquier llamada saliente. Sin esto, un servicio colgado deja colgado
	 * a quien lo llama (tema de resiliencia, S08).
	 */
	@Bean
	RestClientCustomizer tiemposMaximos() {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(2_000);
		fabrica.setReadTimeout(5_000);
		return builder -> builder.requestFactory(fabrica);
	}

	/**
	 * El builder que usan los tres clientes de transferencia. @LoadBalanced hace que Spring Cloud
	 * le agregue un interceptor: el host de la URL (cuenta) se toma como nombre de servicio y se
	 * cambia por la IP y el puerto de una de sus instancias registradas. El configurador aplica lo
	 * mismo que el builder de Spring Boot (entre otras cosas, tiemposMaximos de arriba).
	 */
	@Bean
	@LoadBalanced
	RestClient.Builder restClientBalanceado(RestClientBuilderConfigurer configurador) {
		return configurador.configure(RestClient.builder());
	}

	/**
	 * Un RestClient para el servicio indicado. Si el servicio contesta 4xx o 5xx, se lanza
	 * ErrorRemotoException con el MISMO codigo y el "detail" de su Problem Details, para que
	 * ManejadorDeErrores lo devuelva igual (un 404 de cuenta sigue siendo 404, no 500).
	 */
	public static RestClient crear(RestClient.Builder builder, String urlBase, String servicio) {
		return builder.clone()
			.baseUrl(urlBase)
			.defaultStatusHandler(HttpStatusCode::isError, (peticion, respuesta) -> {
				throw new ErrorRemotoException(servicio, respuesta.getStatusCode().value(), detalle(respuesta, servicio));
			})
			.build();
	}

	private static String detalle(ClientHttpResponse respuesta, String servicio) throws IOException {
		int codigo = respuesta.getStatusCode().value();
		try {
			JsonNode cuerpo = JSON.readTree(respuesta.getBody());
			if (cuerpo != null && cuerpo.hasNonNull("detail")) {
				return cuerpo.get("detail").asText();
			}
		} catch (IOException cuerpoQueNoEsJson) {
			// sin Problem Details: nos quedamos con el codigo
		}
		return "El servicio " + servicio + " respondio " + codigo;
	}
}
