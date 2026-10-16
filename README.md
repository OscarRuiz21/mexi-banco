# Mexi Banco · Saga orquestada y circuit breaker resueltos

Rama: `04-saga-circuit-breaker`. Parte de `03-gateway-discovery-balanceo` (commit `87cfbe9`) y
resuelve el lab S08. Lo de la etapa 03 sigue igual; lo nuevo está en
[v08 · saga orquestada y circuit breaker](#v08--saga-orquestada-y-circuit-breaker).
Caso docente de Sistemas Distribuidos, FI-UNAM, 2027-1.

## Clonar y levantar

Necesitas Docker con Compose y `curl`. Docker construye Java dentro de las imágenes;
no necesitas JDK local para el recorrido. Reserva memoria para varias JVM (al menos
4 GB libres para la etapa 03) y descarga las dependencias antes de clase.

```bash
git clone --branch 04-saga-circuit-breaker https://github.com/OscarRuiz21/mexi-banco.git
cd mexi-banco
docker compose up --build -d
docker compose ps
```

## Qué funciona y cómo comprobarlo

Eureka registra las instancias. El gateway enruta con `lb://` y quita `/api` con
`StripPrefix=1`. Cuenta tiene tres copias. Transferencia usa un `RestClient.Builder`
con `@LoadBalanced` y URLs lógicas sin puerto. Cuenta y SPEI conservan URLs con
nombres de Compose; no todos los consumidores usan discovery.

Espera diez contenedores `healthy`: Postgres, discovery, gateway, tres de cuenta y
los otros cuatro servicios. Solo se publican 8080 (gateway) y 8761 (directorio).

```bash
curl -sS -H 'Accept: application/json' http://localhost:8761/eureka/apps
curl -i http://localhost:8080/api/cuentas/000
curl -i -X POST http://localhost:8080/api/movimientos
./demo-v06.sh
```

El primer 404 viene de cuenta y trae `X-Instancia`; el segundo no coincide con una
ruta del gateway. La demo crea cuentas con 1000 y 500, transfiere 200 (800 y 700),
repite un SPEI de 50 con el mismo ID (750 y 700) y hace nueve consultas mostrando
las tres instancias. Revisa los logs de cuenta durante la transferencia para
observar además el balanceador interno. Los errores finales 404 y 422 no cambian
los saldos. El código de salida del script no sustituye estas comprobaciones.

El heartbeat, el plazo de expiración, el barrido del servidor, su caché de respuestas,
el refresh de los consumidores y la caché del balanceador explican la espera ante
una caída. El balanceador usa listas guardadas; no consulta Eureka en cada petición.
Compose reduce estos tiempos para clase y desactiva la autopreservación de Eureka;
el aviso del tablero corresponde a esa configuración docente.

## v08 · saga orquestada y circuit breaker

**Qué cambió.** `TransferenciaService` es el orquestador de una saga. La transferencia guarda su
estado en la base en cada paso (cada `save` en su propia transacción local, sin `@Transactional`
alrededor de las llamadas HTTP). Si el abono falla después del cargo, se compensa: se devuelve el
monto a origen y queda en el ledger como `TRANSFERENCIA_ENVIADA` más `DEVOLUCION_TRANSFERENCIA`.
Las llamadas de transferencia a cuenta pasan por un circuit breaker de Resilience4j y llevan
timeout explícito (1 s para conectar, 2 s para leer). Cuenta y movimiento aceptan la cabecera
opcional `Idempotency-Key`: con la misma clave, un reintento no vuelve a mover el saldo ni duplica
el asiento. Las notificaciones siguen siendo HTTP síncrono, como en la etapa 03.

**Estados** (`EstadoTransferencia`):

```text
INICIADA ─cargo─▶ CARGADA ─abono─▶ ABONADA ─asientos y avisos─▶ COMPLETADA
   │                 │
   ▼ cargo rechazado ▼ el abono falla: se devuelve el cargo
FALLIDA         COMPENSADA, o COMPENSACION_PENDIENTE si la devolución también falla
                (el reintento programado la lleva a COMPENSADA cuando cuenta vuelve)
```

El abono es el punto sin retorno: si algo falla después de él (asientos o avisos), la fila se
queda en `ABONADA` y no se compensa. Cada paso manda una clave fija derivada de la saga
(`transferencia-<id>-cargo`, `-abono`, `-devolucion`, `-asiento-...`). Por eso el reintento cada
10 s (`ReintentoDeCompensaciones`) no devuelve el dinero dos veces: cuenta guarda la clave en la
misma transacción que el saldo (tabla `operacion_aplicada`, `UNIQUE`). La respuesta es `201` si
quedó `COMPLETADA` y `502` con Problem Details (`estado`, `motivo`, id de la transferencia) si se
compensó o quedó pendiente.

**Provocar la falla del abono con todo arriba.** `FALLAR_ABONO_A_CLABE` hace fallar el abono
hacia esa CLABE dentro de transferencia, sin llamar a cuenta y sin contar para el breaker:

```bash
FALLAR_ABONO_A_CLABE=002180000000000002 docker compose up -d transferencia
curl -i -X POST http://localhost:8080/api/transferencias -H 'Content-Type: application/json' \
  -d '{"claveOrigen":"002180000000000001","claveDestino":"002180000000000002","monto":150}'
curl -s http://localhost:8080/api/movimientos?clabe=002180000000000001
```

**Cuando la compensación también falla** (Parte 0 del lab): levanta transferencia con
`PAUSA_ENTRE_CARGO_Y_ABONO_MS=20000`, lanza una transferencia y apaga cuenta durante la pausa
(`docker compose stop cuenta`). Queda `COMPENSACION_PENDIENTE`. Al hacer
`docker compose start cuenta`, el reintento la termina en `COMPENSADA`. Para ver las pendientes:
`curl -s 'http://localhost:8080/api/transferencias?estado=COMPENSACION_PENDIENTE'`. El gateway
espera 30 s (`GATEWAY_TIEMPO_RESPUESTA`), más que la pausa, para que llegue la respuesta de la saga.

**Ver el breaker.** El circuito `cuenta` se abre si falla la mitad o más de las últimas 4
llamadas. Se queda abierto 10 s (`CIRCUITO_ABIERTO_S`), luego pasa solo a semiabierto. Ahí deja
pasar 2 llamadas de prueba: si salen bien se cierra. Los 404 y 422 de cuenta no cuentan como
falla. El actuator de transferencia no se publica por el gateway; se consulta desde el contenedor:

```bash
docker compose exec transferencia wget -qO- http://localhost:8080/actuator/circuitbreakers
docker compose exec transferencia wget -qO- http://localhost:8080/actuator/circuitbreakerevents
docker compose logs -f transferencia | grep Circuito
```

Apaga cuenta, manda varias transferencias (las primeras tardan el timeout; ya abierto, responden
503 en milisegundos), enciéndela y espera unos segundos: verás `OPEN`, `HALF_OPEN` y `CLOSED`.
Si tu 8080 está ocupado, usa `PUERTO_GATEWAY=18080 docker compose up -d`.

**Lo que esta versión no cubre.** Un timeout de lectura en el cargo o en el abono no prueba que
la operación no ocurrió. Aquí se trata como falla: el cargo queda `FALLIDA` y el abono se
compensa. Lo correcto sería preguntarle a cuenta por la clave antes de decidir. Tampoco hay
recuperación automática de una fila que quedó en `CARGADA` porque el proceso se cayó. La saga
no da aislamiento: entre el cargo y la devolución, quien consulta el saldo de origen ve el dinero
fuera. SPEI sigue sin saga.

## Qué queda fuera

No se recupera la transacción global al agregar gateway y discovery: la saga la sustituye con
compensaciones, no con un rollback. No hay seguridad de producción ni alta disponibilidad del
gateway o del directorio. Ocultar rutas internas al cliente no protege toda la red interna.

## Etapas del recorrido

| Etapa | Rama | Uso |
|---|---|---|
| 01 | `01-monolito` | Leer la operación dentro de un proceso. |
| 02 | `02-separacion` | Seguir la misma operación por HTTP. |
| Lab 03 | `lab/03-gateway-discovery-balanceo` | Construir y observar durante la S07. |
| 03 resuelta | `03-gateway-discovery-balanceo` | Se publica al cierre del lab para comparar. |
| 04 resuelta | `04-saga-circuit-breaker` | Solución del lab S08: saga orquestada y circuit breaker. |

Algunos nombres internos conservan su denominación histórica: scripts como
`demo-v06a.sh` y `demo-v06.sh`, proyectos de Compose, redes, imágenes y contenedores
como `mexi-banco-v06-...`. No son etiquetas de Git ni instrucciones para cambiar de rama.

## Cerrar el entorno

```bash
docker compose down
```

Conserva los volúmenes. Levanta una sola etapa a la vez: comparten puertos y las dos
variantes de la etapa 03 también comparten el proyecto de Compose. Si ya existe un
entorno ajeno usando sus nombres o puertos, detente; no lo apagues.
Usa solamente datos ficticios. Los valores de Postgres del repositorio son de desarrollo.
