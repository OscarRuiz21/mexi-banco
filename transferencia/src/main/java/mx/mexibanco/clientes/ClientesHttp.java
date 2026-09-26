package mx.mexibanco.clientes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.mexibanco.compartido.ErrorRemotoException;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;

/**
 * Como se habla con otro servicio en v06a: RestClient contra una URL base fija que viene de una
 * variable de entorno (MOVIMIENTO_URL=http://movimiento:8080, etc.). Sin discovery ni gateway:
 * si el servicio cambia de puerto o tiene dos replicas, esta URL no se entera. Eso es lo que
 * resuelven Eureka y el gateway en la siguiente version.
 *
 * Esta clase esta copiada igual en cuenta, transferencia y spei (ver README).
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
