# Mexi Banco · Gateway, discovery y balanceo resueltos

Rama: `03-gateway-discovery-balanceo`. Parte del commit `9a65bd6`, versión histórica `feat/v06-gateway-discovery`.
Caso docente de Sistemas Distribuidos, FI-UNAM, 2027-1.

## Clonar y levantar

Necesitas Docker con Compose y `curl`. Docker construye Java dentro de las imágenes;
no necesitas JDK local para el recorrido. Reserva memoria para varias JVM (al menos
4 GB libres para la etapa 03) y descarga las dependencias antes de clase.

```bash
git clone --branch 03-gateway-discovery-balanceo https://github.com/OscarRuiz21/mexi-banco.git
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

## Qué queda fuera

No se recupera la transacción global al agregar gateway y discovery. No hay saga,
seguridad de producción ni alta disponibilidad del gateway o del directorio.
Ocultar rutas internas al cliente no protege toda la red interna.

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
