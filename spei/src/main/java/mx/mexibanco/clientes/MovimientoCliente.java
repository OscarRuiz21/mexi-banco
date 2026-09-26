package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/** Lo que en el monolito era MovimientoService.registrar(...), ahora POST a MOVIMIENTO_URL/movimientos. */
@Component
public class MovimientoCliente {

	record NuevoAsiento(String clabeCuenta, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldoResultante, String referencia) {
	}

	private final RestClient http;

	public MovimientoCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.movimiento}") String urlBase) {
		this.http = ClientesHttp.crear(builder, urlBase, "movimiento");
	}

	public MovimientoRemoto registrar(String clabeCuenta, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldoResultante, String referencia) {
		return http.post()
			.uri("/movimientos")
			.body(new NuevoAsiento(clabeCuenta, tipo, monto, saldoResultante, referencia))
			.retrieve()
			.body(MovimientoRemoto.class);
	}
}
