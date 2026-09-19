# Mexi Banco

Monolito docente de Sistemas Distribuidos (FI-UNAM, 2027-1): un banco ficticio con cinco módulos
(cuenta, movimiento, transferencia, notificación y SPEI) en un solo deployable. Es el caso que
vamos a ir partiendo en servicios durante el semestre, una pieza por sesión. Cada sesión tiene su
etiqueta de git; la de la S05 es `v05.1`.

## Lo que necesitas

- Docker corriendo: `docker compose version` responde.
- Postman de escritorio (https://www.postman.com/downloads/) o, si prefieres, `curl`. La versión
  web de Postman no alcanza tu `localhost`.

## Correrlo

```
git clone --branch v05.1 https://github.com/OscarRuiz21/mexi-banco.git
cd mexi-banco
docker compose up --build
```

Un comando levanta Postgres y la app. `app` espera a que `db` esté realmente lista
(`depends_on` con `condition: service_healthy`, no solo que el contenedor exista) y la encuentra
por su nombre, `db`, sin ninguna IP escrita en ningún lado. Esa es la lección de la S05.

En otra terminal, `docker compose ps` debe mostrar `db` y `app` como `healthy`, no solo
`running`. La app responde en `http://localhost:8080`. Si `app` no llega, revisa
`docker compose logs app`.

Para apagar: `docker compose down` conserva los datos; `docker compose down -v` también borra el
volumen de la base.

## Probarlo

La guía del laboratorio de la S05, petición por petición, está en el repo del grupo:
https://oscarruiz21.github.io/sd-2027-1/labs/s05-mexi-banco/Lab-S05-Mexi-Banco-Postman.html

De ahí se descarga la colección de Postman (`mexi-banco-v05.1.postman_collection.json`): 20
peticiones en cinco carpetas, con 34 pruebas automáticas. En Postman, Import y arrastra el archivo;
en la terminal, `npx newman run mexi-banco-v05.1.postman_collection.json` corre todo de un jalón.

## Credenciales

Usuario, base y contraseña de Postgres valen `mexibanco` en `docker-compose.yml`, en `k8s/` y
como valor por omisión en `application.yml`. Son credenciales de desarrollo, a propósito
visibles: sirven solo para levantar el entorno local de la clase y no dan acceso a nada fuera de
la máquina de quien las corre. En un despliegue real irían en un archivo `.env` o en un secreto,
nunca en el repositorio.

## Endpoints

- `POST /cuentas`: abrir cuenta (`clabe`, `titular`, `saldoInicial`)
- `GET /cuentas/{clabe}`: consultar saldo
- `POST /transferencias`: transferencia interna (`claveOrigen`, `claveDestino`, `monto`)
- `POST /spei`: transferencia a otro banco (requiere la cabecera `Idempotency-Key`)
- `GET /transferencias/{id}`: consultar una transferencia
- `GET /movimientos?clabe=...`: estado de cuenta, del más reciente al más viejo
- `GET /notificaciones?clabe=...`: avisos que recibió la cuenta
- `GET /actuator/health`: lo usa el healthcheck de Compose

Los errores de negocio responden con su código y un cuerpo Problem Details (RFC 9457):
404 si la CLABE o la transferencia no existe, 409 si la CLABE ya está dada de alta o hay un SPEI
en vuelo con la misma clave, 422 si no alcanza el saldo o si origen y destino son la misma cuenta,
400 si al cuerpo le falta un campo. Ninguno es 500.

## Capas

Cada módulo (`cuenta`, `movimiento`, `transferencia`, `notificacion`, `spei`) tiene las mismas
cuatro capas, en `src/main/java/mx/mexibanco`:

| Capa | Qué hace | Ejemplo |
|---|---|---|
| Controlador | Traduce HTTP a una llamada al servicio. Sin reglas de negocio. | `CuentaController` |
| Servicio | Reglas del negocio y transacciones. No sabe nada de HTTP. | `CuentaService` |
| Repositorio | Lee y escribe su tabla. | `CuentaRepository` |
| Entidad | La fila de la tabla. | `Cuenta` |

La regla que importa para lo que sigue del curso: **un módulo solo toca su propio repositorio;
lo de otro módulo lo pide a su servicio.** `TransferenciaService` nunca abre `CuentaRepository`:
llama a `CuentaService.cargar` y `abonar`. Cada llamada entre servicios es una costura. Cuando el
monolito se parta, se vuelve una llamada por red, y la transacción única que hoy envuelve cargo,
abono, asientos y avisos deja de existir. `compartido/` guarda las excepciones de negocio y el
único lugar donde se vuelven códigos HTTP (`ManejadorDeErrores`).

Las pruebas de los servicios (`./mvnw test`) corren sin base ni servidor: otra ganancia de separar
las capas.

## Kubernetes (para la S06, opcional)

La carpeta `k8s/` trae los mismos dos servicios para un clúster local: `k8s/db.yaml` (volumen,
Postgres y el Service `db`) y `k8s/app.yaml` (Deployment con 3 réplicas y el Service
`mexi-banco`). La app no cambia: `DB_HOST=db` lo resuelve ahora el DNS del clúster. Kubernetes se
ve en la S06; esto es para quien quiera probarlo antes, con minikube:

```
minikube start --driver=docker --cpus=4 --memory=6144
docker build -t mexi-banco:latest .
minikube image load mexi-banco:latest
kubectl apply -f k8s/
kubectl get pods -w
```

## A dónde va

Hoy es un solo deployable. En las próximas sesiones se parte: primero en `cuentas`,
`transferencias` y `notificaciones`, después con discovery y gateway, resiliencia y trazas, caché,
eventos y, al final, una saga para el SPEI. Una etiqueta de git por sesión: el historial del repo
es el curso.
