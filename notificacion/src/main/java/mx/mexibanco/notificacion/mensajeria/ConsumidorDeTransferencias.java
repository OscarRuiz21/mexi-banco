package mx.mexibanco.notificacion.mensajeria;

/**
 * LAB S09: el consumidor de los eventos de transferencia. Hoy esta vacio a proposito: notificacion
 * todavia recibe los avisos por HTTP (POST /notificaciones, ver NotificacionController) y esta clase
 * no es un bean de Spring, asi que no hace nada.
 *
 * Lo que va aqui (el enunciado del lab tiene los nombres exactos):
 *  - declarar la cola notificacion.transferencias, enlazada al exchange transferencias con el
 *    patron transferencia.*, y su dead letter exchange y dead letter queue;
 *  - un metodo con @RabbitListener que lea cada evento, guarde los avisos y SOLO DESPUES haga ack;
 *  - que recibir dos veces el mismo evento no deje dos juegos de avisos;
 *  - que un mensaje que no se puede leer termine en la DLQ en vez de atorar la cola.
 */
public class ConsumidorDeTransferencias {

	// LAB S09: tu consumidor va aqui.
}
