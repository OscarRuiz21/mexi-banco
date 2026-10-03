# Mexi Banco · Separación en cinco servicios

Rama: `02-separacion`. Parte del commit `5eee807`, versión histórica `feat/v06a-servicios-partidos`.
Caso docente de Sistemas Distribuidos, FI-UNAM, 2027-1.

## Clonar y levantar

Necesitas Docker con Compose y `curl`. Docker construye Java dentro de las imágenes;
no necesitas JDK local para el recorrido. Reserva memoria para varias JVM (al menos
4 GB libres para la etapa 03) y descarga las dependencias antes de clase.

```bash
git clone --branch 02-separacion https://github.com/OscarRuiz21/mexi-banco.git
cd mexi-banco
docker compose up --build -d
docker compose ps
```

## Qué funciona y cómo comprobarlo

Cinco procesos Java y un Postgres con cinco bases (`db/init/01-crear-bases.sql`).
Espera seis contenedores `healthy`. Cada servicio tiene su base y se comunica por
HTTP: cuenta publica 8081, movimiento 8082, transferencia 8083, notificacion 8084
y spei 8085. Las llamadas internas usan nombres DNS de Compose y el puerto 8080.

```bash
curl -i http://localhost:8081/cuentas/000
./demo-v06a.sh
./demo-v06a.sh --roto
```

El primer curl devuelve un 404 de negocio. En la demo normal, las cuentas parten
con 1000 y 500; tras transferir 200 quedan 800 y 700. SPEI descuenta 50 una sola vez
al repetir la misma clave: quedan 750 y 700. Revisa el mismo ID en los dos SPEI y
los códigos 404, 422, 400 y 409 de los casos inválidos.

`--roto` introduce una pausa y detiene `cuenta` entre cargo y abono: espera 503,
una reducción de 100 en la suma de saldos y ausencia del asiento de ese cargo.
El script recupera cuenta y quita la pausa. Revisa los saldos; su código de salida
por sí solo no verifica el resultado.

## Qué queda fuera

No hay Eureka, gateway ni balanceador Spring Cloud. `transferir()` ya no lleva
`@Transactional`; una transacción local tampoco revertiría escrituras ya confirmadas
vía HTTP en otras bases.
No hay compensación ni saga. La demo muestra esa limitación deliberada.

## Etapas del recorrido

| Etapa | Rama | Uso |
|---|---|---|
| 01 | `01-monolito` | Leer la operación dentro de un proceso. |
| 02 | `02-separacion` | Seguir la misma operación por HTTP. |
| Lab 03 | `lab/03-gateway-discovery-balanceo` | Construir y observar durante la S07. |
| 03 resuelta | `03-gateway-discovery-balanceo` | Se publica al cierre del lab para comparar. |

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
