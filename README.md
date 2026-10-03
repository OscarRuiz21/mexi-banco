# Mexi Banco · Base del laboratorio S07

Rama: `lab/03-gateway-discovery-balanceo`. Parte del commit `f7813c4`, versión histórica `lab/v06-base`.
Caso docente de Sistemas Distribuidos, FI-UNAM, 2027-1.

## Clonar y levantar

Necesitas Docker con Compose y `curl`. Docker construye Java dentro de las imágenes;
no necesitas JDK local para el recorrido. Reserva memoria para varias JVM (al menos
4 GB libres para la etapa 03) y descarga las dependencias antes de clase.

```bash
git clone --branch lab/03-gateway-discovery-balanceo https://github.com/OscarRuiz21/mexi-banco.git
cd mexi-banco
git switch -c trabajo-s07
docker compose up --build -d
docker compose ps
```

## Qué funciona y cómo comprobarlo

Al inicio funcionan los cinco servicios separados y Postgres: seis contenedores
`healthy`, puertos 8081 a 8085. Los módulos `discovery` y `gateway` existen, pero no
se despliegan. Los comentarios `TODO H1`, `TODO H2` y `TODO H3` señalan el trabajo.

```bash
curl -i http://localhost:8081/cuentas/000
docker compose config --services
docker build -t mexi-banco-v06-discovery ./discovery
docker build -t mexi-banco-v06-gateway ./gateway
git show origin/01-monolito:src/main/java/mx/mexibanco/transferencia/TransferenciaService.java
```

Espera 404 de negocio y seis servicios activos en la configuración, sin discovery
ni gateway. Construir sus imágenes anticipadamente no los despliega.
Sigue la guía S07: primero directorio, después rutas, tres copias de cuenta y caída
medida de una copia. Deja el balanceador interno de transferencia para la tarea.
`./demo-v06.sh` solo debe correrse completo después de terminar esa tarea.

Consulta las etapas sin cambiar tu rama de trabajo:

```bash
git diff origin/02-separacion origin/lab/03-gateway-discovery-balanceo -- discovery gateway docker-compose.yml
# Al cierre, una vez que el profesor publique la solución:
git fetch origin refs/heads/03-gateway-discovery-balanceo:refs/remotes/origin/03-gateway-discovery-balanceo
git diff HEAD origin/03-gateway-discovery-balanceo -- discovery gateway transferencia docker-compose.yml
```

Ese último diff compara commits; usa `git diff origin/03-gateway-discovery-balanceo -- ...`
para comparar contra tus archivos aún sin commit. No necesitas `pull`.

## Qué queda fuera

Esta es una base incompleta deliberadamente. No trae directorio, gateway ni balanceo
activados. No incluye saga, seguridad de producción ni tolerancia automática a fallas.

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
