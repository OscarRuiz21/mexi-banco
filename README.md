# Mexi Banco · Monolito

Rama: `01-monolito`. Parte del commit `8820316`, versión histórica `v05.1`.
Caso docente de Sistemas Distribuidos, FI-UNAM, 2027-1.

## Clonar y levantar

Necesitas Docker con Compose y `curl`. Docker construye Java dentro de las imágenes;
no necesitas JDK local para el recorrido. Reserva memoria para varias JVM (al menos
4 GB libres para la etapa 03) y descarga las dependencias antes de clase.

```bash
git clone --branch 01-monolito https://github.com/OscarRuiz21/mexi-banco.git
cd mexi-banco
docker compose up --build -d
docker compose ps
```

## Qué funciona y cómo comprobarlo

Un proceso Java contiene cuenta, movimiento, transferencia, notificación y SPEI.
Compose levanta dos contenedores: `db` y `app`; ambos deben estar `healthy`.
La API está en `http://localhost:8080`. Esta rama conserva la compatibilidad de
código, API, Docker y Kubernetes con la etiqueta `v05.1`: solo cambia este README.

```bash
curl -i http://localhost:8080/actuator/health
LAB_CLABE="S07$(date +%s)"
curl -i -X POST http://localhost:8080/cuentas \
  -H 'Content-Type: application/json' \
  -d "{\"clabe\":\"$LAB_CLABE\",\"titular\":\"Prueba local\",\"saldoInicial\":0}"
curl -i "http://localhost:8080/cuentas/$LAB_CLABE"
curl -i http://localhost:8080/cuentas/000
```

Espera salud `UP`, alta 201, consulta 200 con saldo 0 y consulta inexistente 404.
La transferencia interna se recibe en `POST /transferencias` con `claveOrigen`,
`claveDestino` y `monto`. Consulta asientos en `GET /movimientos?clabe=...` y avisos
 en `GET /notificaciones?clabe=...`. `POST /spei` requiere `Idempotency-Key`.
Lee `src/main/java/mx/mexibanco/transferencia/TransferenciaService.java`: su
`@Transactional` puede envolver las escrituras locales del recorrido.

## Qué queda fuera

No hay separación por HTTP entre módulos, directorio, gateway ni balanceo del lado
del cliente. `k8s/` conserva la demo histórica de Kubernetes; no se usa en la S07.

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
