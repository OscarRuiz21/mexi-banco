package mx.mexibanco.notificacion.avisos;

/**
 * v11: por donde se le avisa a un cliente. Cada canal necesita datos distintos: un SMS necesita un
 * telefono, un correo necesita una direccion, un push necesita el token del dispositivo. Por eso el
 * aviso guardado en MongoDB tiene una forma distinta segun el canal (ver BitacoraDeAvisos).
 *
 * Es una interfaz sellada (Java 17+): solo existen estos tres canales, y un switch sobre Contacto que
 * olvide uno no compila.
 */
public sealed interface Contacto {

	record Sms(String telefono) implements Contacto {
	}

	record Correo(String correo) implements Contacto {
	}

	record Push(String token) implements Contacto {
	}
}
