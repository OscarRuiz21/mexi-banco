# Mexi Banco

Caso docente de Sistemas Distribuidos (FI-UNAM, 2027-1): un banco ficticio con cinco piezas
(cuenta, movimiento, transferencia, notificación y SPEI) que vamos partiendo durante el semestre.
Cada sesión tiene su etiqueta de git y el historial del repo es el curso.

Esta es la **v06: el banco partido vuelve a hablar, por una puerta y con un directorio**. Sobre los
cinco servicios de v06a llegan tres piezas:

- **`discovery`**: un servidor **Eureka**, el directorio. Cada servicio se registra al arrancar con
  su nombre lógico (`cuenta`, `movimiento`, ...) y su IP, y manda un latido cada tanto.
- **`gateway`**: **Spring Cloud Gateway**, la única puerta. El cliente solo conoce
  `http://localhost:8080/api/...`; el gateway decide a qué servicio va cada petición y, con
  `lb://`, a cuál de sus instancias.
- **Balanceo del lado del cliente**: `transferencia` ya no tiene escrita la dirección de `cuenta`.
  Llama a `http://cuenta` (un nombre, sin puerto) con un `RestClient` `@LoadBalanced`, y `cuenta`
  corre en **tres réplicas** que se reparten las peticiones en round robin.

Versiones: Spring Boot 3.3.4, Java 21, Spring Cloud **2023.0.3** (Eureka 4.1.3, Gateway 4.1.5).
Ojo si subes Spring Cloud: 2024.0 ya pide Boot 3.4, y dentro de 2023.0.x, de la 2023.0.4 en adelante
el gateway usa `HttpHeaders.headerSet()`, que solo existe desde Spring Framework 6.1.15. Con Boot
3.3.4 (Framework 6.1.13) compila y arranca, pero la primera petición que reenvía truena con
`NoSuchMethodError` y el cliente recibe una respuesta vacía. `gateway/.../ReenvioGatewayTest` lo
detecta. Los cinco servicios de v06a siguen en la etiqueta `v06a`.

## Lo que necesitas

- Docker corriendo: `docker compose version` responde.
- `curl` (o Postman de escritorio; la versión web de Postman no alcanza tu `localhost`).
- Unos **4 GB de RAM libres para Docker**: son diez contenedores (`db`, `discovery`,
  `gateway`, tres `cuenta` y los otros cuatro servicios) y juntos usan alrededor de 2.7 a 3.1 GB (medido con `docker stats`).
  En Docker Desktop: Settings, Resources, Memory.

## Correrlo

```
git clone --branch v06 https://github.com/OscarRuiz21/mexi-banco.git
cd mexi-banco
docker compose up --build -d
docker compose ps
```

La primera vez tarda: son siete imágenes y cada una descarga sus dependencias de Maven por su
cuenta (entre 4.5 y 6 minutos en frío en la laptop del profesor; en una laptop más modesta o con internet lento,
cuenta el doble). Las siguientes veces Docker usa su caché y el arranque es de segundos.

`docker compose ps` debe mostrar los diez contenedores como `healthy` (entre 30 y 60 s con las imágenes ya
construidas). Luego:

- Dashboard del directorio: http://localhost:8761 (instancias registradas, una fila por servicio).
- La puerta: `curl -i localhost:8080/api/cuentas/000` responde 404 **de cuenta** (Problem Details).
  Si responde 503 es que el gateway todavía no ve a `cuenta` en el directorio: espera unos
  segundos.

Para apagar: `docker compose down` conserva los datos; `docker compose down -v` también borra el
volumen de la base.

## Una puerta: rutas del gateway

Desde tu máquina solo hay dos puertos: **8080** (el gateway, para todo) y **8761** (el dashboard
de Eureka, para mirar). Los servicios ya no publican puerto; adentro de la red de Compose siguen
escuchando en el 8080.

| Por la puerta | Va a | Llega al servicio como |
|---|---|---|
| `POST /api/cuentas`, `GET /api/cuentas/{clabe}` | `lb://cuenta` (3 réplicas) | `/cuentas`, `/cuentas/{clabe}` |
| `GET /api/movimientos?clabe=...` | `lb://movimiento` | `/movimientos?clabe=...` |
| `POST /api/transferencias`, `GET /api/transferencias/{id}` | `lb://transferencia` | `/transferencias`, `/transferencias/{id}` |
| `GET /api/notificaciones?clabe=...` | `lb://notificacion` | `/notificaciones?clabe=...` |
| `POST /api/spei` (con `Idempotency-Key`) | `lb://spei` | `/spei` |

Las rutas están en `gateway/src/main/resources/application.yml`. Cada una tiene predicados
(`Path`, `Method`), un destino `lb://<nombre>` y el filtro `StripPrefix=1`, que quita el `/api`.
`lb://cuenta` quiere decir "pregúntale al directorio qué instancias de `cuenta` hay y reparte".

Los endpoints **internos** de v06a (`POST /cuentas/{clabe}/cargos` y `/abonos`, `POST /movimientos`,
`POST /notificaciones`) **no tienen ruta**: por la puerta responden 404. Por eso la ruta de cuenta
usa `{clabe}` (un solo segmento) y no `/**`, y movimiento y notificación solo aceptan `GET`. Siguen
abiertos dentro de la red de Compose, que es donde los usan los servicios; protegerlos de verdad
es tema de seguridad.

## Un directorio: Eureka

Cada servicio trae el cliente de Eureka (`spring-cloud-starter-netflix-eureka-client`) y en su
`application.yml` dice dónde está el directorio (`EUREKA_URL`). Se registra con
`spring.application.name` y con la IP de su contenedor (`prefer-ip-address: true`); el ID de la
instancia lleva el hostname, que es el ID corto del contenedor (el mismo que ves en `docker ps`).

El registro es **eventualmente consistente**, y hay varias copias que pueden ir atrás:

| Qué | Propiedad | Spring Cloud | En este compose |
|---|---|---|---|
| Latido de cada instancia | `eureka.instance.lease-renewal-interval-in-seconds` | 30 s | 5 s (`EUREKA_LATIDO_S`) |
| Sin latido por tanto tiempo, se da por muerta | `eureka.instance.lease-expiration-duration-in-seconds` | 90 s | 15 s (`EUREKA_EXPIRA_S`) |
| El servidor barre a las muertas cada | `eureka.server.eviction-interval-timer-in-ms` | 60 s | 5 s (`EUREKA_BARRIDO_MS`) |
| El servidor refresca lo que ven los clientes cada | `eureka.server.response-cache-update-interval-ms` | 30 s | 5 s (`EUREKA_CACHE_MS`) |
| Cada cliente pide la lista completa cada | `eureka.client.registry-fetch-interval-seconds` | 30 s | 5 s (`EUREKA_CONSULTA_S`) |
| El balanceador guarda la lista por | `spring.cloud.loadbalancer.cache.ttl` | 35 s | 5 s (`BALANCEADOR_CACHE`) |
| Autopreservación | `eureka.server.enable-self-preservation` | sí | no (`EUREKA_AUTOPRESERVACION`) |

Los valores de Spring Cloud están pensados para producción (no saturar el directorio con miles de
latidos); en clase los bajamos para ver en segundos lo que de otro modo tarda minutos. Todos se
pueden cambiar desde la terminal sin tocar archivos, por ejemplo para ver los de Spring Cloud:

```
EUREKA_LATIDO_S=30 EUREKA_EXPIRA_S=90 EUREKA_CONSULTA_S=30 BALANCEADOR_CACHE=35s \
EUREKA_BARRIDO_MS=60000 EUREKA_CACHE_MS=30000 EUREKA_AUTOPRESERVACION=true docker compose up -d
```

Medido en la laptop del profesor con `./demo-v06.sh --latido` (matar una réplica con `docker kill`,
es decir, sin que alcance a despedirse):

| | Valores de clase | Valores de Spring Cloud |
|---|---|---|
| Réplica muerta sale del registro | 33 a 37 s | 197 s |
| Peticiones con error por la puerta mientras tanto | 1 de cada 3 a 4, hasta los ~31 s | 24 de 87, hasta los 248 s |
| Réplica reiniciada aparece en el registro (desde `docker start`) | 6 a 10 s | 13 a 17 s |
| El gateway le vuelve a mandar peticiones | 13 a 16 s | 53 a 65 s |

Dos detalles que sorprenden:

- **Tarda más que "la expiración".** Eureka marca una instancia como vencida cuando pasan *dos*
  veces la expiración sin latido (un error viejo de Eureka que se conserva por compatibilidad y
  que el propio código documenta), y luego hay que esperar al barrido, a la caché del servidor y a
  la del cliente. Con 90 s de expiración, la cuenta real es de minutos.
- **Por qué la puerta falla más tiempo que el registro.** El gateway no le pregunta a Eureka en
  cada petición: usa su copia (consulta cada 30 s) y encima la caché del balanceador (35 s). Con
  los valores de Spring Cloud, la réplica muerta salió del registro a los 197 s pero el gateway le
  siguió mandando peticiones hasta los 248 s.
- **`docker stop` no es una caída.** En un apagado ordenado Spring marca la instancia `DOWN` en el
  directorio y el balanceador deja de escogerla (con los valores de clase, cero errores por la
  puerta). La entrada se queda en el registro como `DOWN` hasta que vence su contrato (19 s con
  valores de clase, 133 s con los de Spring Cloud), porque el aviso final de baja no alcanza a
  llegar antes de que termine el proceso. Con los valores de Spring Cloud el `DOWN` tarda 13 s en
  verse (la caché del servidor) y hubo errores por la puerta hasta los 28 s. Para ver el latido en
  acción hay que matar el proceso: `docker kill`.

**Autopreservación y el aviso rojo.** Si de golpe faltan muchos latidos, Eureka supone que el
problema es la red y no saca a nadie: prefiere responder con datos quizá viejos (AP). Con pocas
instancias, una sola caída basta para activarla, así que en este compose está apagada y el
dashboard lo dice en rojo: *THE SELF PRESERVATION MODE IS TURNED OFF. THIS MAY NOT PROTECT
INSTANCE EXPIRY IN CASE OF NETWORK/OTHER PROBLEMS.* Es esperado. Cuando la autopreservación sí se
activa, el aviso es *EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE
NOT...* y nadie sale del registro.

En este compose, con la autopreservación encendida y los valores de Spring Cloud, matar una réplica
**no** la activó (se midió: la baja llegó igual a los 197 s). La cuenta: el umbral es el 85 % de los
latidos esperados por minuto (13 a 15 aquí) y el dashboard mostraba 32 latidos por minuto con ocho
instancias, el doble de lo esperado, porque un Eureka solo se anota a sí mismo como réplica
(`localhost:8761`, que el dashboard lista en *unavailable-replicas*) y cuenta dos veces cada latido.
Con una réplica muerta quedan 28, muy arriba del umbral.

## Balanceo: tres réplicas de cuenta

`cuenta` corre con `deploy.replicas: 3` y sin `ports` (tres contenedores no pueden publicar el
mismo puerto de tu máquina). Para cambiar el número: `docker compose up -d --scale cuenta=2`.

Cada respuesta de `cuenta` trae la cabecera **`X-Instancia`** con el hostname de la réplica que
contestó:

```
for i in 1 2 3 4 5 6; do curl -s -D - -o /dev/null localhost:8080/api/cuentas/000 | grep -i x-instancia; done
```

Hay dos balanceadores, los dos del lado del cliente y en round robin:

- **El gateway** hacia cualquier servicio (`lb://`).
- **`transferencia` hacia `cuenta`, `movimiento` y `notificacion`**: en
  `transferencia/.../clientes/ClientesHttp.java` hay un `RestClient.Builder` con `@LoadBalanced`,
  y las URLs son `http://cuenta`, `http://movimiento`, `http://notificacion`, sin puerto. Antes de
  cada petición el balanceador cambia el nombre por la IP y el puerto de una instancia registrada.
  El cargo y el abono de una misma transferencia pueden ir a réplicas distintas:
  `docker compose logs cuenta | grep "atendida por"`.

`cuenta` y `spei` siguen llamando a sus vecinos con la URL de Compose (`http://movimiento:8080`,
`http://cuenta:8080`): el DNS de Docker reparte entre las réplicas, pero sin enterarse de quién
está sano. Pasarlos al directorio es el mismo cambio que en `transferencia`.

Otro riesgo nuevo, dicho claro: el directorio y la puerta son **dos piezas más que pueden caerse**.
Sin `gateway`, el cliente no entra; sin `discovery`, los clientes siguen con la última lista que
tenían (por eso Eureka es AP) pero nadie nuevo se puede registrar.

## Probarlo paso a paso

Con todo `healthy`. Si ya corriste esto antes, cambia las CLABEs o responderá 409. Todo va por el
**8080**.

```
curl -X POST localhost:8080/api/cuentas -H 'Content-Type: application/json' \
  -d '{"clabe":"002180000000000001","titular":"Ana","saldoInicial":1000}'
curl -X POST localhost:8080/api/cuentas -H 'Content-Type: application/json' \
  -d '{"clabe":"002180000000000002","titular":"Beto","saldoInicial":500}'
curl -X POST localhost:8080/api/transferencias -H 'Content-Type: application/json' \
  -d '{"claveOrigen":"002180000000000001","claveDestino":"002180000000000002","monto":200}'
curl localhost:8080/api/transferencias/1
curl -i localhost:8080/api/cuentas/002180000000000001      # mira X-Instancia
curl localhost:8080/api/cuentas/002180000000000002
curl 'localhost:8080/api/movimientos?clabe=002180000000000001'
curl 'localhost:8080/api/notificaciones?clabe=002180000000000002'
curl -X POST localhost:8080/api/spei -H 'Content-Type: application/json' -H 'Idempotency-Key: spei-001' \
  -d '{"claveOrigen":"002180000000000001","bancoDestino":"BANCO-X","claveDestino":"012180000000000009","monto":50}'
curl -i -X POST localhost:8080/api/cuentas/002180000000000001/abonos \
  -H 'Content-Type: application/json' -d '{"monto":1000000}'   # 404: sin puerta
```

### Todo de un jalón

`./demo-v06.sh` hace lo anterior con títulos por paso: espera a que el directorio conozca a todos,
abre cuentas, transfiere y muestra a qué réplicas fueron el cargo y el abono, comprueba que los
saldos suman, hace nueve consultas seguidas para ver el round robin, prueba que los internos no
tienen puerta y propaga los errores de negocio. `./demo-v06.sh --latido` además mata una réplica
de `cuenta`, cronometra cuánto tarda en salir del directorio (y cuántas peticiones fallan por la
puerta mientras tanto), la vuelve a prender y cronometra cuánto tarda en regresar. No borra nada.

## Lo que se rompió al partir (sigue roto)

El directorio y la puerta no arreglan lo que se perdió en v06a: cargo y abono siguen siendo dos
peticiones que se confirman cada una por su lado. Con tres réplicas de `cuenta` incluso puede que
el cargo lo haga una y el abono otra, y si esa segunda se cae a la mitad, el dinero queda en el
aire igual que antes. La demostración paso a paso está en la etiqueta `v06a` (`demo-v06a.sh --roto`);
arreglarlo es el tema de sagas, S12.

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
con los de v06a ni v05.1 si los tienes en la misma máquina.

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

Las pruebas de cada servicio, del gateway y del directorio corren sin base, sin red externa y
sin contenedores (las del gateway y la de balanceo de `transferencia` levantan Spring en memoria):

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
`git checkout v05.1`. Los cinco servicios sin directorio ni puerta, en `v06a`.

## A dónde va

Ya hay puerta y directorio. Lo que sigue: resiliencia y trazas (S08; hoy una réplica caída
produce errores hasta que el directorio se entera, y nadie reintenta), caché, eventos y, al final,
una saga para lo que se rompió al partir.
