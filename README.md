# Mexi Banco

Caso docente de Sistemas Distribuidos (FI-UNAM, 2027-1): un banco ficticio con cinco piezas
(cuenta, movimiento, transferencia, notificación y SPEI) que vamos partiendo durante el semestre.
Cada sesión tiene su etiqueta de git y el historial del repo es el curso.

> **Rama `lab/v06-base`: punto de partida del laboratorio de la S07.** Tal como está, levanta lo
> mismo que v06a (todo lo de abajo sigue valiendo). Ya existen los módulos `discovery` (Eureka) y
> `gateway` (Spring Cloud Gateway), compilan y tienen su Dockerfile, pero les faltan piezas marcadas
> con comentarios `TODO H1`, `TODO H2` y `TODO H3`. Para verlas todas:
>
> ```
> grep -rn "TODO H" --include=*.java --include=*.yml --include=pom.xml .
> ```
>
> | Hito | Qué logras | Dónde |
> |---|---|---|
> | H1 | `discovery` arriba y los cinco servicios en el dashboard de Eureka (http://localhost:8761) | `discovery/` (anotación y dos propiedades), el `pom.xml` de cada servicio (una dependencia) y `docker-compose.yml` |
> | H2 | El cliente usa un solo puerto: `http://localhost:8080/api/...` | rutas en `gateway/src/main/resources/application.yml` y `docker-compose.yml` |
> | H3 | `transferencia` llama a `cuenta` por nombre, `cuenta` en tres réplicas y el round robin a la vista (cabecera `X-Instancia`) | `transferencia/.../clientes/ClientesHttp.java` y `docker-compose.yml` |
>
> Después de cada hito: `docker compose up --build -d` y `docker compose ps`. Cada hito tiene una
> prueba desactivada que sirve de comprobación: borra su `@Disabled` y corre `./mvnw test` en el
> módulo. Al terminar los tres, `./demo-v06.sh` corre completo, todo por el puerto 8080.

Esta es la **v06a: el monolito partido en cinco servicios**. Cada uno tiene su propio proyecto
Maven, su Dockerfile, su contenedor y su base de datos, y se hablan por HTTP. Todavía no hay
discovery ni gateway: eso llega en la siguiente versión, y esta existe justo para sentir por qué
hace falta. El monolito sigue vivo en la etiqueta `v05.1`.

## Lo que necesitas

- Docker corriendo: `docker compose version` responde.
- `curl` (o Postman de escritorio; la versión web de Postman no alcanza tu `localhost`).
- Unos 2 GB de RAM libres para Docker: los seis contenedores usan alrededor de 1.5 GB.

## Correrlo

```
git clone --branch v06a https://github.com/OscarRuiz21/mexi-banco.git
cd mexi-banco
docker compose up --build
```

La primera vez tarda: son cinco imágenes y cada una descarga sus dependencias de Maven por su
cuenta (unos 7 minutos en frío). Las siguientes veces Docker usa su caché y el arranque es de
segundos.

En otra terminal, `docker compose ps` debe mostrar los seis contenedores (`db` y los cinco
servicios) como `healthy`. Si alguno no llega, `docker compose logs <servicio>`.

Para apagar: `docker compose down` conserva los datos; `docker compose down -v` también borra el
volumen de la base.

## Servicios y puertos

| Servicio | Puerto en tu máquina | Base propia | Llama a | Endpoints públicos |
|---|---|---|---|---|
| `cuenta` | 8081 | `cuenta` | movimiento | `POST /cuentas`, `GET /cuentas/{clabe}` |
| `movimiento` | 8082 | `movimiento` | nadie | `GET /movimientos?clabe=...` |
| `transferencia` | 8083 | `transferencia` | cuenta, movimiento, notificacion | `POST /transferencias`, `GET /transferencias/{id}` |
| `notificacion` | 8084 | `notificacion` | nadie | `GET /notificaciones?clabe=...` |
| `spei` | 8085 | `spei` | cuenta, movimiento, notificacion | `POST /spei` (con cabecera `Idempotency-Key`) |

Todos exponen `GET /actuator/health`, que usa el healthcheck de Compose. Dentro de la red de
Compose todos escuchan en el 8080 y se encuentran por nombre: `transferencia` llama a
`http://cuenta:8080`. La URL de cada vecino viene de una variable de entorno (`CUENTA_URL`,
`MOVIMIENTO_URL`, `NOTIFICACION_URL`), no del código (factor III).

### Endpoints internos nuevos

En el monolito, transferencia y SPEI llamaban a métodos de otros módulos. Ahora esos métodos son
endpoints HTTP. Son internos: los usan otros servicios, no el cliente.

| Endpoint | Servicio | Reemplaza a | Lo usan |
|---|---|---|---|
| `POST /cuentas/{clabe}/cargos` con `{"monto": 200}` | cuenta | `CuentaService.cargar` | transferencia, spei |
| `POST /cuentas/{clabe}/abonos` con `{"monto": 200}` | cuenta | `CuentaService.abonar` | transferencia |
| `POST /movimientos` con `{clabeCuenta, tipo, monto, saldoResultante, referencia}` | movimiento | `MovimientoService.registrar` | cuenta, transferencia, spei |
| `POST /notificaciones` con `{clabeCuenta, tipo, monto, saldoResultante}` | notificacion | `NotificacionService.notificar` | transferencia, spei |

Hoy nada los protege: cualquiera con `curl` puede abonarse dinero en el 8081. Ocultarlos detrás de
un gateway y ponerles seguridad es parte de lo que sigue.

## El dolor, a propósito

- **El cliente tiene que saberse cinco puertos.** Para abrir una cuenta va al 8081, para
  transferir al 8083, para ver su estado de cuenta al 8082. Si un servicio cambia de puerto, todos
  los clientes se enteran por las malas.
- **Cada servicio tiene escrita la dirección de los demás.** Si levantas dos réplicas de `cuenta`,
  `transferencia` sigue llamando a `http://cuenta:8080` y nadie decide a cuál de las dos ir, ni se
  entera si una se cayó.

Un **gateway** (una sola puerta hacia afuera) y el **discovery** (los servicios se registran y se
buscan por nombre lógico) resuelven eso en la siguiente versión.

## Probarlo paso a paso

Con todo `healthy` y la base recién creada. Si ya corriste esto antes, cambia las CLABEs o
responderá 409.

Abrir dos cuentas (servicio cuenta):

```
curl -X POST localhost:8081/cuentas -H 'Content-Type: application/json' \
  -d '{"clabe":"002180000000000001","titular":"Ana","saldoInicial":1000}'
curl -X POST localhost:8081/cuentas -H 'Content-Type: application/json' \
  -d '{"clabe":"002180000000000002","titular":"Beto","saldoInicial":500}'
```

Transferir 200 de Ana a Beto (servicio transferencia) y consultar la transferencia:

```
curl -X POST localhost:8083/transferencias -H 'Content-Type: application/json' \
  -d '{"claveOrigen":"002180000000000001","claveDestino":"002180000000000002","monto":200}'
curl localhost:8083/transferencias/1
```

Saldos (esperado: 800 y 700, la suma sigue en 1500):

```
curl localhost:8081/cuentas/002180000000000001
curl localhost:8081/cuentas/002180000000000002
```

Estado de cuenta y avisos:

```
curl 'localhost:8082/movimientos?clabe=002180000000000001'
curl 'localhost:8084/notificaciones?clabe=002180000000000002'
```

SPEI de 50 a otro banco, y el mismo SPEI otra vez con la misma `Idempotency-Key`: responde la
misma solicitud (mismo `id`) y no cobra dos veces. El saldo de Ana queda en 750.

```
curl -X POST localhost:8085/spei -H 'Content-Type: application/json' -H 'Idempotency-Key: spei-001' \
  -d '{"claveOrigen":"002180000000000001","bancoDestino":"BANCO-X","claveDestino":"012180000000000009","monto":50}'
curl -X POST localhost:8085/spei -H 'Content-Type: application/json' -H 'Idempotency-Key: spei-001' \
  -d '{"claveOrigen":"002180000000000001","bancoDestino":"BANCO-X","claveDestino":"012180000000000009","monto":50}'
curl localhost:8081/cuentas/002180000000000001
```

Un error que cruza servicios: transferir a una CLABE que no existe. El 404 lo dice `cuenta` y
`transferencia` lo devuelve igual, con `"servicio": "cuenta"` en el cuerpo:

```
curl -X POST localhost:8083/transferencias -H 'Content-Type: application/json' \
  -d '{"claveOrigen":"002180000000000001","claveDestino":"002180000000000099","monto":10}'
```

### Todo de un jalón

`./demo-v06a.sh` recorre lo anterior con títulos por paso, más los errores de negocio (404, 422,
400 y 409). Genera CLABEs nuevas en cada corrida, así que se puede repetir. `./demo-v06a.sh --roto`
agrega la demostración de la siguiente sección. No borra nada.

## Lo que se rompió al partir

En v05.1, `TransferenciaService.transferir` era **una sola transacción local**: cargo, abono,
asientos y avisos se confirmaban juntos o ninguno. Ahora cada paso es una petición HTTP a otro
servicio, con su propia base, y cada una se confirma sola en cuanto responde. Ya no hay
transacción que abarque tres procesos y cuatro bases. **Si el cargo funciona y el abono falla, el
dinero ya salió de origen y nunca llega a destino.** Nadie lo regresa.

Se deja así a propósito. Arreglarlo (compensar el cargo, o no cobrar hasta estar seguros) es el
tema de sagas, S12.

Para verlo con tus ojos (lo hace `./demo-v06a.sh --roto`):

1. Levanta `transferencia` con una pausa de demo de 8 segundos entre el cargo y el abono:
   `PAUSA_ENTRE_CARGO_Y_ABONO_MS=8000 docker compose up -d transferencia`
2. Lanza una transferencia de 100 y, durante la pausa, `docker compose stop cuenta`.
3. `transferencia` responde **503** (no pudo contactar a `cuenta` para el abono).
4. `docker compose start cuenta` y consulta los saldos: a Ana le faltan 100, Beto no los recibió,
   la suma bajó 100. Y en `/movimientos` de Ana no aparece ese cargo: el saldo y el estado de
   cuenta ya no cuadran.
5. Quita la pausa: `docker compose up -d transferencia`.

Otra variante, sin pausa: `docker compose stop movimiento` y transfiere. Cargo y abono sí ocurren
en `cuenta`, pero el asiento falla y el cliente recibe 503. El dinero se movió, no hay
transferencia registrada ni asientos, y si el cliente reintenta porque "falló", se mueve otra vez.
Regresa con `docker compose start movimiento`.

Lo mismo le pasa a SPEI: si el cargo sale bien y `movimiento` falla, la solicitud se queda en
`PROCESANDO` y un reintento con la misma clave responde 409 para siempre.

## Sin módulo compartido

No hay una librería común entre servicios, y es a propósito: cada servicio se construye y se
despliega solo, sin esperar a que otro equipo publique una versión nueva de un `.jar` compartido.
El precio es la duplicación, y está a la vista:

- `compartido/` (las excepciones de negocio y `ManejadorDeErrores`) está copiado igual en los cinco.
- `clientes/` (los `RestClient` hacia otros servicios y sus DTOs, como `CuentaRemota` o la copia
  del enum `TipoMovimiento`) está copiado en `cuenta`, `transferencia` y `spei`.
- `notificacion` no conoce el enum de movimiento: recibe el tipo como texto.

Si `movimiento` agrega un tipo nuevo, las copias no se enteran solas. Ese es el trato.

## Datos

Un solo contenedor de Postgres con **una base por servicio** (`cuenta`, `movimiento`,
`transferencia`, `notificacion`, `spei`), creadas por `db/init/01-crear-bases.sql`. Cada servicio
solo conoce el nombre de la suya y nadie hace consultas contra la base de otro. En producción
serían instancias separadas; aquí comparten contenedor para que la demo quepa en una laptop.

Postgres corre el script de init solo la primera vez, con el volumen vacío. Por eso esta versión
usa su propio nombre de proyecto de Compose (`mexi-banco-v06`), su red y su volumen, y no choca
con los de v05.1 si los tienes en la misma máquina.

## Errores

Cada servicio responde sus errores de negocio con Problem Details (RFC 9457): 404 si la CLABE o la
transferencia no existe, 409 si la CLABE ya está dada de alta o hay un SPEI en vuelo con la misma
clave, 422 si no alcanza el saldo o si origen y destino son la misma cuenta, 400 si falta un campo
o la cabecera `Idempotency-Key`.

Cuando el error viene de otro servicio, se propaga **con el mismo código** (el 422 de saldo
insuficiente que dice `cuenta` le llega al cliente de `transferencia` como 422, no como 500), con
el campo `servicio` diciendo quién lo originó. Si el otro servicio ni siquiera contesta, la
respuesta es 503.

## Capas

Cada servicio conserva las cuatro capas del monolito, en `<servicio>/src/main/java/mx/mexibanco`:

| Capa | Qué hace | Ejemplo |
|---|---|---|
| Controlador | Traduce HTTP a una llamada al servicio. Sin reglas de negocio. | `CuentaController` |
| Servicio | Reglas del negocio. No sabe nada de HTTP. | `TransferenciaService` |
| Repositorio | Lee y escribe su tabla, en su base. | `TransferenciaRepository` |
| Entidad | La fila de la tabla. | `Transferencia` |

Lo que en el monolito era "pedirle al servicio de otro módulo" ahora es un cliente HTTP en
`clientes/` (`CuentaCliente`, `MovimientoCliente`, `NotificacionCliente`). Para
`TransferenciaService` se sigue viendo como una llamada a método; por dentro es la red.

Las pruebas de cada servicio corren sin base, sin red y sin contenedores:

```
cd transferencia && ./mvnw test
```

## Credenciales

Usuario y contraseña de Postgres valen `mexibanco` en `docker-compose.yml` y como valor por
omisión en cada `application.yml`. Son credenciales de desarrollo, a propósito visibles: sirven
solo para levantar el entorno local de la clase y no dan acceso a nada fuera de la máquina de
quien las corre. En un despliegue real irían en un archivo `.env` o en un secreto, y cada servicio
tendría su propio usuario.

## El monolito y Kubernetes

El monolito (un solo `pom.xml` en la raíz) y la carpeta `k8s/` siguen en la etiqueta `v05.1`:
`git checkout v05.1`.

## A dónde va

Ya está partido. Lo que sigue: discovery y gateway, resiliencia y trazas, caché, eventos y, al
final, una saga para lo que aquí se rompió.
