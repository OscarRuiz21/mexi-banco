package mx.mexibanco.clientes;

import java.math.BigDecimal;

/** Lo que el servicio cuenta devuelve en JSON. Copia local: no importamos la entidad de otro servicio. */
public record CuentaRemota(String clabe, String titular, BigDecimal saldo) {
}
