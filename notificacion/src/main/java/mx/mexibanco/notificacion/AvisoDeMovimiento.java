package mx.mexibanco.notificacion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Lo que notificacion necesita saber de un movimiento para avisar. En el monolito recibia la entidad
 * Movimiento; ahora recibe este DTO por HTTP. Es una copia parcial del JSON que devuelve el servicio
 * movimiento: los campos que sobran se ignoran. El tipo llega como texto, a proposito: notificacion
 * no necesita conocer el enum de otro servicio.
 */
public record AvisoDeMovimiento(@NotBlank String clabeCuenta, @NotBlank String tipo,
								@NotNull BigDecimal monto, @NotNull BigDecimal saldoResultante) {
}
