package mx.mexibanco.clientes;

import mx.mexibanco.compartido.ErrorRemotoException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Nueva en v06a: el cliente HTTP de cuenta, contra un servidor falso (sin red ni contenedores).
 * Comprueba la regla de errores: lo que responde el servicio remoto se propaga con el mismo codigo.
 */
class ClientesHttpTest {

	private static final String URL = "http://cuenta:8080";

	@Test
	void unCargoExitosoRegresaLaCuentaConSuNuevoSaldo() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
		servidor.expect(requestTo(URL + "/cuentas/002180000000000001/cargos"))
			.andExpect(method(org.springframework.http.HttpMethod.POST))
			.andExpect(content().json("{\"monto\":200}"))
			.andRespond(withSuccess("{\"clabe\":\"002180000000000001\",\"titular\":\"Ana\",\"saldo\":800.00}", MediaType.APPLICATION_JSON));

		CuentaRemota cuenta = new CuentaCliente(builder, URL).cargar("002180000000000001", new BigDecimal("200"));

		assertThat(cuenta.saldo()).isEqualByComparingTo("800");
		servidor.verify();
	}

	@Test
	void un404DeCuentaSePropagaComo404ConSuDetalle() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
		servidor.expect(requestTo(URL + "/cuentas/999"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,\"detail\":\"No existe la CLABE 999\"}"));

		assertThatThrownBy(() -> new CuentaCliente(builder, URL).consultar("999"))
			.isInstanceOfSatisfying(ErrorRemotoException.class, e -> {
				assertThat(e.getEstado()).isEqualTo(404);
				assertThat(e.getServicio()).isEqualTo("cuenta");
				assertThat(e.getMessage()).isEqualTo("No existe la CLABE 999");
			});
	}

	@Test
	void un422DeSaldoInsuficienteSePropagaComo422() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
		servidor.expect(requestTo(URL + "/cuentas/1/cargos"))
			.andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body("{\"status\":422,\"detail\":\"Saldo insuficiente en la CLABE 1\"}"));

		assertThatThrownBy(() -> new CuentaCliente(builder, URL).cargar("1", BigDecimal.TEN))
			.isInstanceOfSatisfying(ErrorRemotoException.class, e -> assertThat(e.getEstado()).isEqualTo(422));
	}
}
