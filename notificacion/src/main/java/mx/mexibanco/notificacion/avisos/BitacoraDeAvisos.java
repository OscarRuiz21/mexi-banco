package mx.mexibanco.notificacion.avisos;

import mx.mexibanco.notificacion.EventoTransferencia;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * v11: la bitacora de avisos enviados, en la coleccion "avisos" de MongoDB. Un documento por aviso.
 *
 * Por que documentos: cada aviso tiene una forma distinta segun el canal (SMS con telefono, CORREO con
 * direccion y asunto, PUSH con token), se escribe una vez, no se actualiza y se lee entero por CLABE.
 * No hay joins ni invariantes entre avisos. Es lo contrario del saldo, que se queda en Postgres.
 *
 * Que va aqui (lab S11, Parte 2):
 *  - documento(): arma el aviso con su _id y los campos de su canal;
 *  - registrar(): inserta cada aviso y, si el _id ya existe, NO lo cuenta (un evento repetido no
 *    duplica el aviso; es la idempotencia de la S09 sin tabla aparte);
 *  - crearIndice(): el indice por clabe y fecha que usa ultimos();
 *  - ultimos(): los 10 avisos mas recientes de una CLABE.
 *
 * Ya esta resuelto: a quien se avisa por cada tipo de evento (destinatarios) y por que canal
 * (DirectorioDeContactos).
 */
@Component
public class BitacoraDeAvisos {

	private static final Logger log = LoggerFactory.getLogger(BitacoraDeAvisos.class);

	public static final String COLECCION = "avisos";
	public static final int ULTIMOS = 10;

	private final MongoTemplate mongo;
	private final DirectorioDeContactos contactos;

	public BitacoraDeAvisos(MongoTemplate mongo, DirectorioDeContactos contactos) {
		this.mongo = mongo;
		this.contactos = contactos;
	}

	/** Un aviso por hacer: a que CLABE, de que tipo y con que saldo resultante. */
	public record Destinatario(String clabe, String tipo, BigDecimal saldo) {
	}

	/**
	 * Las mismas reglas de la S09: TransferenciaRealizada avisa a origen y a destino; TransferenciaRevertida
	 * solo a origen, porque su dinero regreso.
	 */
	public static List<Destinatario> destinatarios(EventoTransferencia evento) {
		return switch (evento.tipo()) {
			case EventoTransferencia.REALIZADA -> List.of(
				new Destinatario(evento.clabeOrigen(), "TRANSFERENCIA_ENVIADA", evento.saldoOrigen()),
				new Destinatario(evento.clabeDestino(), "TRANSFERENCIA_RECIBIDA", evento.saldoDestino()));
			case EventoTransferencia.REVERTIDA -> List.of(
				new Destinatario(evento.clabeOrigen(), "DEVOLUCION_TRANSFERENCIA", evento.saldoOrigen()));
			default -> throw new IllegalArgumentException("Tipo de evento desconocido: " + evento.tipo());
		};
	}

	/** El texto de siempre: tipo, monto y saldo resultante. */
	public static String texto(Destinatario destinatario, EventoTransferencia evento) {
		return destinatario.tipo() + " por " + evento.monto().abs() + ", saldo resultante " + destinatario.saldo();
	}

	/**
	 * Guarda los avisos de un evento y regresa cuantos eran NUEVOS (0 si el evento ya se habia procesado).
	 */
	public int registrar(EventoTransferencia evento) {
		// TODO S11: por cada destinatario, mongo.insert(documento(...), COLECCION); cuenta los nuevos y atrapa DuplicateKeyException (ya estaba: no se cuenta, no es error).
		throw new UnsupportedOperationException("TODO S11: BitacoraDeAvisos.registrar");
	}

	/**
	 * El aviso como documento de MongoDB. Los campos comunes van siempre; los del canal, segun el canal.
	 */
	public static Document documento(EventoTransferencia evento, Destinatario destinatario, Contacto contacto) {
		// TODO S11: _id = eventoId + ":" + clabe (un evento genera hasta dos avisos), mas eventoId, transferenciaId, clabe, tipo, monto y saldo (como Decimal128) y fecha (Date.from(evento.fecha())).
		// TODO S11: switch (contacto): Sms agrega canal "SMS", telefono y texto; Correo agrega canal "CORREO", correo, asunto y cuerpo; Push agrega canal "PUSH", token, titulo y cuerpo.
		throw new UnsupportedOperationException("TODO S11: BitacoraDeAvisos.documento");
	}

	/** Se corre una vez al arrancar. Crear un indice que ya existe igual no hace nada. */
	@EventListener(ApplicationReadyEvent.class)
	public void crearIndice() {
		// TODO S11: mongo.indexOps(COLECCION).ensureIndex(...) con clabe ascendente y fecha descendente, llamado "clabe_fecha".
		log.info("[avisos] todavia no hay indice en {} (TODO S11)", COLECCION);
	}

	/** Los ULTIMOS avisos de una CLABE, del mas reciente al mas viejo, tal como estan guardados. */
	public List<Document> ultimos(String clabe) {
		// TODO S11: mongo.find(Query con clabe igual, ordenada por fecha descendente y limit(ULTIMOS), Document.class, COLECCION).
		return List.of();
	}
}
