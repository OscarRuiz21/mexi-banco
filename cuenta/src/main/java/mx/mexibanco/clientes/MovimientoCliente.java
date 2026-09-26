package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Lo que en el monolito era MovimientoService.registrar(...), ahora POST http://movimiento:8080/movimientos.
 * Para quien lo usa parece una llamada a metodo; por dentro es la red.
 */
@Component
public class MovimientoCliente {

	record NuevoAsiento(String clabeCuenta, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldoResultante, String referencia) {
	}

	private final RestClient http;

	public MovimientoCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.movimiento}") String urlBase) {
		this.http = ClientesHttp.crear(builder, urlBase, "movimiento");
	}

	public void registrar(String clabeCuenta, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldoResultante, String referencia) {
		http.post()
			.uri("/movimientos")
			.body(new NuevoAsiento(clabeCuenta, tipo, monto, saldoResultante, referencia))
			.retrieve()
			.toBodilessEntity();
	}
}
