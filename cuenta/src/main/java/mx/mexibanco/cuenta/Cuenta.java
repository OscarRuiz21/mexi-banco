package mx.mexibanco.cuenta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import java.math.BigDecimal;

/**
 * Uno de los cuatro conceptos de Mexi Banco: la CLABE identifica la cuenta, nunca su id interno.
 * El saldo vive aqui como cache derivado; la verdad son los Movimiento (ver Movimiento.java).
 */
@Entity
public class Cuenta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 18)
	private String clabe;

	@Column(nullable = false)
	private String titular;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal saldo;

	@Version
	private Long version;

	protected Cuenta() {
	}

	public Cuenta(String clabe, String titular, BigDecimal saldoInicial) {
		this.clabe = clabe;
		this.titular = titular;
		this.saldo = saldoInicial;
	}

	public Long getId() {
		return id;
	}

	public String getClabe() {
		return clabe;
	}

	public String getTitular() {
		return titular;
	}

	public BigDecimal getSaldo() {
		return saldo;
	}

	public void aplicar(BigDecimal delta) {
		this.saldo = this.saldo.add(delta);
	}
}
