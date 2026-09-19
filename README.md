# Mexi Banco

Monolito docente para Sistemas Distribuidos (FI-UNAM, 2027-1). Cuatro conceptos y ni uno más
(cuenta, movimiento, transferencia, notificación), más SPEI como quinto — la tabla completa está
en `00-PLAN-MAESTRO.md` §4 del curso. No es el proyecto de los alumnos ni reemplaza a
`microservices-ecommerce`: es el caso hilo conductor, ahora también como código corriendo, no solo
como ejemplo hablado.

## Correrlo

```
docker compose up --build
```

Un comando levanta Postgres y la app. `app` espera a que `db` esté realmente lista
(`depends_on` + `healthcheck`, no solo que el contenedor exista) y la encuentra por nombre
(`db`), sin ninguna IP en ningún lado — esa es la lección de la S05.

## Credenciales

Usuario, base y contraseña de Postgres valen `mexibanco` en `docker-compose.yml`, en `k8s/` y
como valor por omisión en `application.yml`. Son credenciales de desarrollo, a propósito
visibles: sirven solo para levantar el entorno local de la clase y no dan acceso a nada fuera de
la máquina de quien las corre. En un despliegue real irían en un secreto, no en el repositorio.

## Endpoints

- `POST /cuentas` — abrir cuenta (`clabe`, `titular`, `saldoInicial`)
- `GET /cuentas/{clabe}` — consultar saldo
- `POST /transferencias` — transferencia interna (`claveOrigen`, `claveDestino`, `monto`)
- `POST /spei` — transferencia externa (requiere cabecera `Idempotency-Key`)
- `GET /transferencias/{id}` — consultar una transferencia
- `GET /movimientos?clabe=...` — estado de cuenta, del más reciente al más viejo
- `GET /notificaciones?clabe=...` — avisos que recibió la cuenta
- `GET /actuator/health` — para el healthcheck de Compose y las probes de Kubernetes

Los errores de negocio responden con su código y un cuerpo Problem Details (RFC 9457):
404 si la CLABE o la transferencia no existe, 409 si la CLABE ya está dada de alta o hay un SPEI
en vuelo con la misma clave, 422 si no alcanza el saldo o si origen y destino son la misma cuenta,
400 si al cuerpo le falta un campo.

## Capas

Cada módulo (`cuenta`, `movimiento`, `transferencia`, `notificacion`, `spei`) tiene las mismas
cuatro capas:

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

## Kubernetes (demo del profesor, S05)

```
minikube start --driver=docker --cpus=4 --memory=6144
docker build -t mexi-banco:latest .
minikube image load mexi-banco:latest
kubectl apply -f k8s/
kubectl get pods -w
```

`k8s/db.yaml` (PVC + Postgres + Service `db`) y `k8s/app.yaml` (Deployment con 3 réplicas +
Service `mexi-banco`). La app no cambia: `DB_HOST=db` lo resuelve ahora el DNS del clúster.

## Roadmap: de monolito a piezas, sesión a sesión

Hoy es un solo deployable, etiquetado `v05`. El plan completo, sesión por sesión y en el estilo
de las secciones del curso de DevTalles (material con derechos de autor, local en
`referencias/`, fuera de este repositorio), está en el curso:
`SD_final_2026/06-MEXI-BANCO-EVOLUCION.md`. En corto: `v06a` lo parte en `cuentas`,
`transferencias` y `notificaciones` sin comunicación entre ellos; `v06` agrega discovery y
gateway; `v08` resiliencia y trazas; `v09` caché del saldo; `v10` eventos; `v11` Kafka para
antifraude; `v12` la saga del SPEI con outbox. Una etiqueta de git por sesión: el historial del
repo es el curso. No adelantar piezas antes de su sesión.
