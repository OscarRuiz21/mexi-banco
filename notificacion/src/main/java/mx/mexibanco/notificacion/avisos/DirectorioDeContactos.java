package mx.mexibanco.notificacion.avisos;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * v11: a quien y por donde se avisa. Datos de juguete y fijos, para el lab: Ana (CLABE ...001) quiere
 * SMS, Beto (...002) quiere correo y cualquier otra cuenta recibe un push. En un banco real esto son
 * las preferencias del cliente, y tambien serian buenos candidatos a documentos (ver el reto del lab).
 */
@Component
public class DirectorioDeContactos {

	private static final Map<String, Contacto> CONOCIDOS = Map.of(
		"002180000000000001", new Contacto.Sms("+52 55 5555 0101"),
		"002180000000000002", new Contacto.Correo("beto@correo.mx"));

	public Contacto de(String clabe) {
		return CONOCIDOS.getOrDefault(clabe, new Contacto.Push("fcm:demo-" + clabe));
	}
}
