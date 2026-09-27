package mx.mexibanco.cuenta;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Nueva en v06: toda respuesta de cuenta dice que replica la atendio. */
class CabeceraInstanciaTest {

	@Test
	void laRespuestaTraeElNombreDeLaReplica() throws Exception {
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/cuentas/002180000000000001");
		MockHttpServletResponse respuesta = new MockHttpServletResponse();
		MockFilterChain cadena = new MockFilterChain();

		new CabeceraInstancia("a1b2c3d4e5f6").doFilter(peticion, respuesta, cadena);

		assertThat(respuesta.getHeader("X-Instancia")).isEqualTo("a1b2c3d4e5f6");
		assertThat(cadena.getRequest()).as("la peticion sigue su camino al controlador").isSameAs(peticion);
	}
}
