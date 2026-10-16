package mx.mexibanco.cuenta;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Nuevo en v08: la memoria de cargos y abonos que llegaron con Idempotency-Key. Se guarda en la
 * MISMA transaccion local que cambia el saldo (CuentaService): o quedan los dos o ninguno.
 *
 * La columna clave es UNIQUE, igual que en SpeiSolicitud: si dos peticiones con la misma clave
 * llegan juntas, la base deja insertar a una y la transaccion de la otra se deshace (con todo y su
 * cambio de saldo). Guarda tambien el saldo con el que quedo la cuenta, para contestarle lo mismo a
 * un reintento en vez de recalcularlo.
 */
@Entity
public class OperacionAplicada {

	public enum Tipo { CARGO, ABONO }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String clave;

	@Column(nullable = false, length = 18)
	private String clabe;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Tipo tipo;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal saldoResultante;

	@Column(nullable = false)
	private Instant fecha;

	protected OperacionAplicada() {
	}

	public OperacionAplicada(String clave, String clabe, Tipo tipo, BigDecimal monto, BigDecimal saldoResultante) {
		this.clave = clave;
		this.clabe = clabe;
		this.tipo = tipo;
		this.monto = monto;
		this.saldoResultante = saldoResultante;
		this.fecha = Instant.now();
	}

	/** Mismo cargo o abono, a la misma cuenta y por el mismo monto: un reintento legitimo. */
	public boolean esLaMisma(String clabe, Tipo tipo, BigDecimal monto) {
		return this.clabe.equals(clabe) && this.tipo == tipo && this.monto.compareTo(monto) == 0;
	}

	public String getClave() {
		return clave;
	}

	public String getClabe() {
		return clabe;
	}

	public BigDecimal getSaldoResultante() {
		return saldoResultante;
	}
}
