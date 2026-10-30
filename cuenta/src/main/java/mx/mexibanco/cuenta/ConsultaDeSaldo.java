package mx.mexibanco.cuenta;

import mx.mexibanco.compartido.NoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * v11: la consulta que el cliente hace para VER su saldo (GET /cuentas/{clabe}). Es la unica que se
 * puede servir desde la cache.
 *
 * Por que una clase aparte y no @Cacheable en CuentaService.consultar: CuentaService.cargar tambien
 * llama a consultar para autorizar el cargo, y esa lectura NUNCA debe salir de la cache. Ademas, una
 * llamada dentro de la misma clase no pasa por el proxy de Spring y la anotacion se ignoraria sin
 * avisar. Con dos clases queda escrito en el codigo: esta muestra, CuentaService decide.
 */
@Service
public class ConsultaDeSaldo {

	private static final Logger log = LoggerFactory.getLogger(ConsultaDeSaldo.class);

	private final CuentaRepository cuentas;

	public ConsultaDeSaldo(CuentaRepository cuentas) {
		this.cuentas = cuentas;
	}

	// TODO S11: @Cacheable(cacheNames = ConfiguracionCache.CUENTAS, key = "#clabe"): en un hit contesta Redis y este metodo no corre; en un miss corre, lee Postgres y guarda el resultado.
	public CuentaVista consultar(String clabe) {
		// Esta linea sale en el log SOLO cuando se lee Postgres. Con la cache puesta, en un hit no aparece.
		log.info("[consulta] {} leida de Postgres", clabe);
		return cuentas.findByClabe(clabe)
			.map(CuentaVista::de)
			.orElseThrow(() -> new NoEncontradoException("No existe la CLABE " + clabe));
	}
}
