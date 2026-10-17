package mx.mexibanco.transferencia;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static mx.mexibanco.transferencia.EstadoTransferencia.*;

/**
 * La operacion completa, una fila por transferencia. Los asientos (cargo, abono, devolucion) viven
 * en movimiento; aqui queda el registro de la operacion y, desde v08, EL ESTADO DE LA SAGA: en que
 * paso va, por que fallo si fallo, y lo que la compensacion necesita para hacerse sin preguntar a
 * nadie (el saldo de origen despues del cargo, para su asiento).
 *
 * Cada metodo marcar...() es una transicion de la maquina de estados de EstadoTransferencia. Si se
 * pide una que no existe (por ejemplo, de COMPLETADA a COMPENSADA), truena: es un error de programacion.
 *
 * Las columnas nuevas de v08 se dejan sin NOT NULL a proposito: ddl-auto=update las agrega a una
 * tabla que puede traer filas de v06 (mismo volumen de Postgres) y un NOT NULL sin valor tronaria.
 * Lo mismo con eventoId (v09).
 */
@Entity
public class Transferencia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 18)
	private String claveOrigen;

	@Column(nullable = false, length = 18)
	private String claveDestino;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false)
	private Instant fecha;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private EstadoTransferencia estado;

	/** Por que fallo o por que se compenso, en palabras. Null en el camino feliz. */
	@Column(length = 500)
	private String motivo;

	@Column(precision = 19, scale = 2)
	private BigDecimal saldoOrigenTrasCargo;

	@Column
	private Integer intentosDeCompensacion;

	@Column
	private Instant actualizada;

	/**
	 * v09: el id del evento que se publica al terminar la saga (TransferenciaRealizada o
	 * TransferenciaRevertida; nunca las dos). Nace con la fila y se guarda con ella, asi que si el
	 * mismo evento se publica otra vez (un reintento), sale con el MISMO id y el consumidor lo
	 * reconoce como repetido. Si se generara al publicar, cada reintento pareceria un evento nuevo.
	 */
	@Column(unique = true)
	private UUID eventoId;

	protected Transferencia() {
	}

	public Transferencia(String claveOrigen, String claveDestino, BigDecimal monto) {
		this.claveOrigen = claveOrigen;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.fecha = Instant.now();
		this.estado = INICIADA;
		this.intentosDeCompensacion = 0;
		this.actualizada = this.fecha;
		this.eventoId = UUID.randomUUID();
	}

	/**
	 * v09: una fila de v08 (mismo volumen de Postgres) no trae eventoId. Al leerla de la base se le
	 * asigna uno, y queda guardado con el siguiente save de la saga (por ejemplo, cuando el reintento
	 * programado termina una compensacion pendiente de antes de v09).
	 */
	@PostLoad
	void asegurarEventoId() {
		if (eventoId == null) {
			eventoId = UUID.randomUUID();
		}
	}

	/**
	 * La clave de idempotencia de cada paso: el id de la saga mas el nombre del paso. Es estable:
	 * el reintento de un paso manda la MISMA clave, y por eso el otro servicio lo reconoce y no lo
	 * aplica dos veces. Ejemplo: transferencia-12-devolucion.
	 */
	public String clave(String paso) {
		return "transferencia-" + id + "-" + paso;
	}

	public void marcarCargada(BigDecimal saldoOrigen) {
		pasarA(CARGADA, INICIADA);
		this.saldoOrigenTrasCargo = saldoOrigen;
	}

	public void marcarFallida(String motivo) {
		pasarA(FALLIDA, INICIADA);
		this.motivo = motivo;
	}

	public void marcarAbonada() {
		pasarA(ABONADA, CARGADA);
	}

	public void marcarCompletada() {
		pasarA(COMPLETADA, ABONADA);
	}

	/** El primer intento llega con el motivo del abono fallido; los reintentos, con null (se conserva el primero). */
	public void marcarCompensacionPendiente(String motivo) {
		pasarA(COMPENSACION_PENDIENTE, CARGADA, COMPENSACION_PENDIENTE);
		contarIntento(motivo);
	}

	public void marcarCompensada(String motivo) {
		pasarA(COMPENSADA, CARGADA, COMPENSACION_PENDIENTE);
		contarIntento(motivo);
	}

	private void contarIntento(String motivo) {
		this.intentosDeCompensacion = getIntentosDeCompensacion() + 1;
		if (motivo != null) {
			this.motivo = motivo;
		}
	}

	private void pasarA(EstadoTransferencia nuevo, EstadoTransferencia... desde) {
		if (!Set.of(desde).contains(estado)) {
			throw new IllegalStateException("La transferencia " + id + " no puede pasar de " + estado + " a " + nuevo);
		}
		this.estado = nuevo;
		this.actualizada = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getClaveOrigen() {
		return claveOrigen;
	}

	public String getClaveDestino() {
		return claveDestino;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public Instant getFecha() {
		return fecha;
	}

	public EstadoTransferencia getEstado() {
		return estado;
	}

	public String getMotivo() {
		return motivo;
	}

	public BigDecimal getSaldoOrigenTrasCargo() {
		return saldoOrigenTrasCargo;
	}

	public int getIntentosDeCompensacion() {
		return intentosDeCompensacion == null ? 0 : intentosDeCompensacion;
	}

	public Instant getActualizada() {
		return actualizada;
	}

	public UUID getEventoId() {
		return eventoId;
	}
}
