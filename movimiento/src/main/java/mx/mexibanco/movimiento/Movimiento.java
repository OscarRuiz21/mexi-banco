package mx.mexibanco.movimiento;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Segundo concepto de Mexi Banco: el asiento es inmutable. No hay update ni delete a proposito
 * (ni en el repositorio ni expuesto por ningun controller): el ledger solo crece.
 */
@Entity
public class Movimiento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String clabeCuenta;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TipoMovimiento tipo;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal monto;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal saldoResultante;

	@Column(nullable = false)
	private Instant fecha;

	@Column
	private String referencia;

	protected Movimiento() {
	}

	public Movimiento(String clabeCuenta, TipoMovimiento tipo, BigDecimal monto, BigDecimal saldoResultante, String referencia) {
		this.clabeCuenta = clabeCuenta;
		this.tipo = tipo;
		this.monto = monto;
		this.saldoResultante = saldoResultante;
		this.referencia = referencia;
		this.fecha = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getClabeCuenta() {
		return clabeCuenta;
	}

	public TipoMovimiento getTipo() {
		return tipo;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public BigDecimal getSaldoResultante() {
		return saldoResultante;
	}

	public Instant getFecha() {
		return fecha;
	}

	public String getReferencia() {
		return referencia;
	}
}
