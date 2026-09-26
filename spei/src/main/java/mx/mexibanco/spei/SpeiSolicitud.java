package mx.mexibanco.spei;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Quinto componente de la tabla de Mexi Banco: externo, lento e irreversible (00-PLAN-MAESTRO.md §4).
 * El INSERT con UNIQUE sobre idempotencyKey es a la vez la comprobacion y la reserva, tal como
 * lo vieron en la T01 (Idempotency-Key, RFC 9110, patron de Stripe): ver SpeiController.
 */
@Entity
public class SpeiSolicitud {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String idempotencyKey;

	@Column(nullable = false)
	private String claveOrigen;

	@Column(nullable = false)
	private String bancoDestino;

	@Column(nullable = false)
	private String claveDestino;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EstadoSpei estado;

	@Column(nullable = false)
	private Instant creadoEn;

	protected SpeiSolicitud() {
	}

	public SpeiSolicitud(String idempotencyKey, String claveOrigen, String bancoDestino, String claveDestino, BigDecimal monto) {
		this.idempotencyKey = idempotencyKey;
		this.claveOrigen = claveOrigen;
		this.bancoDestino = bancoDestino;
		this.claveDestino = claveDestino;
		this.monto = monto;
		this.estado = EstadoSpei.PROCESANDO;
		this.creadoEn = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getClaveOrigen() {
		return claveOrigen;
	}

	public String getClaveDestino() {
		return claveDestino;
	}

	public String getBancoDestino() {
		return bancoDestino;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public EstadoSpei getEstado() {
		return estado;
	}

	public void marcarEnviado() {
		this.estado = EstadoSpei.ENVIADO;
	}
}
