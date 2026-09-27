package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Lo que en el monolito era CuentaService.consultar/cargar/abonar, ahora por HTTP a CUENTA_URL
 * (en v06, http://cuenta: el nombre en el directorio, y una replica distinta en cada peticion).
 * Cada metodo es una peticion independiente que se confirma sola en el servicio cuenta:
 * no hay transaccion que envuelva un cargo y el abono que le sigue.
 */
@Component
public class CuentaCliente {

	private final RestClient http;

	public CuentaCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.cuenta}") String urlBase) {
		this.http = ClientesHttp.crear(builder, urlBase, "cuenta");
	}

	public CuentaRemota consultar(String clabe) {
		return http.get().uri("/cuentas/{clabe}", clabe).retrieve().body(CuentaRemota.class);
	}

	public CuentaRemota cargar(String clabe, BigDecimal monto) {
		return http.post().uri("/cuentas/{clabe}/cargos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class);
	}

	public CuentaRemota abonar(String clabe, BigDecimal monto) {
		return http.post().uri("/cuentas/{clabe}/abonos", clabe).body(Map.of("monto", monto))
			.retrieve().body(CuentaRemota.class);
	}
}
