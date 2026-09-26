package mx.mexibanco.clientes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Lo que en el monolito era NotificacionService.notificar(movimiento), ahora POST a
 * NOTIFICACION_URL/notificaciones con el movimiento en JSON. Sigue siendo sincrono: si notificacion
 * esta caido, quien llama se entera con un error aunque el dinero ya se haya movido. Pasarlo a un
 * broker (asincrono) es tema de una sesion posterior.
 */
@Component
public class NotificacionCliente {

	private final RestClient http;

	public NotificacionCliente(RestClient.Builder builder, @Value("${mexibanco.servicios.notificacion}") String urlBase) {
		this.http = ClientesHttp.crear(builder, urlBase, "notificacion");
	}

	public void notificar(MovimientoRemoto movimiento) {
		http.post().uri("/notificaciones").body(movimiento).retrieve().toBodilessEntity();
	}
}
