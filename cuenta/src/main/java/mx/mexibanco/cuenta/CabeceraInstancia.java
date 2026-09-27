package mx.mexibanco.cuenta;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Nueva en v06, solo para ver el balanceo: cada respuesta de cuenta dice que replica la atendio,
 * en la cabecera X-Instancia (el hostname del contenedor, que es su ID corto de Docker y aparece
 * igual en el dashboard de Eureka). Tambien deja una linea en el log por peticion, para ver en
 * `docker compose logs cuenta` como transferencia reparte el cargo y el abono entre replicas.
 */
@Component
public class CabeceraInstancia extends OncePerRequestFilter {

	public static final String CABECERA = "X-Instancia";

	private static final Logger log = LoggerFactory.getLogger(CabeceraInstancia.class);

	private final String instancia;

	public CabeceraInstancia() {
		this(nombreDeEsteHost());
	}

	CabeceraInstancia(String instancia) {
		this.instancia = instancia;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
		throws ServletException, IOException {
		respuesta.setHeader(CABECERA, instancia);
		if (!peticion.getRequestURI().startsWith("/actuator")) {
			log.info("{} {} atendida por {}", peticion.getMethod(), peticion.getRequestURI(), instancia);
		}
		cadena.doFilter(peticion, respuesta);
	}

	private static String nombreDeEsteHost() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "desconocida";
		}
	}
}
