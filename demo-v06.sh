#!/usr/bin/env bash
# Demo de Mexi Banco v06: una puerta (gateway) y un directorio (Eureka).
#
# Uso (con todo arriba y healthy: docker compose up --build -d ; docker compose ps):
#   ./demo-v06.sh            flujo completo por el gateway (un solo puerto) y el round robin de cuenta
#   ./demo-v06.sh --latido   ademas, matar una replica de cuenta y cronometrar cuanto tarda el
#                            directorio en darse cuenta, y cuanto tarda en volver a verla
#
# Todo va por http://localhost:8080 (el gateway). El 8761 (Eureka) solo se consulta para mostrar
# el registro. No borra nada: en modo --latido mata una replica (docker kill) y la vuelve a prender.
# Las CLABEs cambian en cada corrida, asi que se puede correr varias veces seguidas.

set -u
MODO="${1:-}"
cd "$(dirname "$0")"

PUERTA=http://localhost:8080/api
EUREKA=http://localhost:8761
PROYECTO=mexi-banco-v06

SELLO=$(date +%s | cut -c3-10)          # 8 digitos que cambian en cada corrida
ANA="00218000${SELLO}01"
BETO="00218000${SELLO}02"
NADIE="00218000${SELLO}99"             # nunca se da de alta
LLAVE="spei-demo-${SELLO}"

NEGRITA=$'\033[1m'; AZUL=$'\033[34m'; NORMAL=$'\033[0m'

paso() { printf '\n%s=== %s ===%s\n' "$NEGRITA" "$1" "$NORMAL"; }
nota() { printf '%s-> %s%s\n' "$AZUL" "$1" "$NORMAL"; }

# Hace la peticion, la muestra y deja el cuerpo en $CUERPO y el codigo en $CODIGO.
pedir() {
	local arg linea='$ curl'
	for arg in "$@"; do
		case $arg in
			*[!A-Za-z0-9_./:=?-]*) linea="$linea '$arg'" ;;
			*) linea="$linea $arg" ;;
		esac
	done
	printf '%s\n' "$linea"
	local salida
	salida=$(curl -s -w '\n%{http_code}' "$@")
	CODIGO=${salida##*$'\n'}
	CUERPO=${salida%$'\n'*}
	if command -v jq >/dev/null 2>&1 && [ -n "$CUERPO" ]; then
		printf '%s\n' "$CUERPO" | jq . 2>/dev/null || printf '%s\n' "$CUERPO"
	else
		printf '%s\n' "$CUERPO"
	fi
	printf '[HTTP %s]\n' "$CODIGO"
}

# Saca un campo de un JSON plano sin depender de jq: campo saldo
campo() { printf '%s' "$CUERPO" | sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" | head -1; }

post() { local url=$1 cuerpo=$2; shift 2; pedir -X POST "$url" -H 'Content-Type: application/json' "$@" -d "$cuerpo"; }

suma() { awk "BEGIN { printf \"%.2f\", $1 + $2 }"; }
saldo_de() { CUERPO=$(curl -s "$PUERTA/cuentas/$1"); campo saldo; }

# Que replica de cuenta contesto: la cabecera X-Instancia (hostname = ID corto del contenedor).
instancia_que_responde() { curl -s -D - -o /dev/null "$PUERTA/cuentas/$1" | tr -d '\r' | sed -n 's/^[Xx]-[Ii]nstancia: //p'; }

# Nombre de Compose de un contenedor a partir de su ID corto: a1b2c3d4e5f6 -> mexi-banco-v06-cuenta-2
nombre_de() { docker inspect -f '{{.Name}}' "$1" 2>/dev/null | sed 's#^/##'; }

# Cuantas instancias de una aplicacion hay en el directorio, sin jq: cada instancia trae su "app".
instancias_en_eureka() {
	curl -s -H 'Accept: application/json' "$EUREKA/eureka/apps" | grep -o "\"app\":\"$1\"" | wc -l | tr -d ' '
}

# El registro completo, una linea por aplicacion: cuantas instancias tiene.
registro() {
	curl -s -H 'Accept: application/json' "$EUREKA/eureka/apps" | grep -o '"app":"[A-Z]*"' | sort | uniq -c |
		sed 's/"app"://; s/"//g' | awk '{ printf "   %-14s %s instancia(s)\n", $2, $1 }'
}

# ---------------------------------------------------------------------------------------------
paso "0. Esperar a que el directorio conozca a todos y la puerta encuentre a cuenta"
INICIO=$(date +%s)
until [ "$(instancias_en_eureka CUENTA)" -ge 3 ] 2>/dev/null &&
	[ "$(instancias_en_eureka TRANSFERENCIA)" -ge 1 ] && [ "$(instancias_en_eureka MOVIMIENTO)" -ge 1 ] &&
	[ "$(instancias_en_eureka NOTIFICACION)" -ge 1 ] && [ "$(instancias_en_eureka SPEI)" -ge 1 ] &&
	[ "$(curl -s -o /dev/null -w '%{http_code}' "$PUERTA/cuentas/$NADIE")" = "404" ]; do
	if [ $(( $(date +%s) - INICIO )) -gt 180 ]; then
		printf 'No quedo listo en 180 s. Revisa docker compose ps y http://localhost:8761\n'; exit 1
	fi
	printf '.'; sleep 2
done
nota "listo en $(( $(date +%s) - INICIO )) s. Lo que sabe el directorio ($EUREKA/eureka/apps):"
registro
nota "404 de cuenta (no 503 del gateway) para una CLABE que no existe: la puerta ya sabe llegar"

paso "1. Abrir la cuenta de Ana con 1000 (por la puerta: POST /api/cuentas)"
post "$PUERTA/cuentas" "{\"clabe\":\"$ANA\",\"titular\":\"Ana\",\"saldoInicial\":1000}"
nota "el gateway quito /api y lo mando a lb://cuenta: a una de sus tres replicas"

paso "2. Abrir la cuenta de Beto con 500 (misma puerta)"
post "$PUERTA/cuentas" "{\"clabe\":\"$BETO\",\"titular\":\"Beto\",\"saldoInicial\":500}"

paso "3. Transferir 200 de Ana a Beto (misma puerta: POST /api/transferencias)"
sleep 1; DESDE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
post "$PUERTA/transferencias" "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$BETO\",\"monto\":200}"
ID_TRANSFERENCIA=$(campo id)
nota "transferencia llamo a http://cuenta (un nombre, sin puerto). A que replicas fue cada llamada:"
sleep 1
docker compose logs --since "$DESDE" cuenta 2>/dev/null | grep 'atendida por' | sort -t'|' -k2 |
	sed 's/^\([^ |]*\) *|.*CabeceraInstancia *: /   \1  /'
nota "consulta de origen y destino, cargo y abono: cada llamada la reparte el balanceador de transferencia"

paso "4. Consultar esa transferencia"
pedir "$PUERTA/transferencias/$ID_TRANSFERENCIA"

paso "5. Saldos: el dinero no se crea ni se pierde"
pedir "$PUERTA/cuentas/$ANA"
pedir "$PUERTA/cuentas/$BETO"
SA=$(saldo_de "$ANA"); SB=$(saldo_de "$BETO")
nota "Ana $SA + Beto $SB = $(suma "$SA" "$SB") (antes: 1000 + 500 = 1500). Esperado: 800 y 700"

paso "6. Estado de cuenta de Ana y avisos de Beto (GET /api/movimientos, GET /api/notificaciones)"
pedir "$PUERTA/movimientos?clabe=$ANA"
pedir "$PUERTA/notificaciones?clabe=$BETO"

paso "7. SPEI de 50 con Idempotency-Key, dos veces (POST /api/spei)"
post "$PUERTA/spei" "{\"claveOrigen\":\"$ANA\",\"bancoDestino\":\"BANCO-X\",\"claveDestino\":\"012180000000000009\",\"monto\":50}" -H "Idempotency-Key: $LLAVE"
ID_SPEI=$(campo id)
post "$PUERTA/spei" "{\"claveOrigen\":\"$ANA\",\"bancoDestino\":\"BANCO-X\",\"claveDestino\":\"012180000000000009\",\"monto\":50}" -H "Idempotency-Key: $LLAVE"
nota "mismo id ($ID_SPEI -> $(campo id)): no se cobro dos veces. Saldo de Ana: $(saldo_de "$ANA") (esperado 750)"

paso "8. Round robin: 9 consultas seguidas a la misma cuenta, por la misma puerta"
VISTAS=""
ANTERIOR=""; SEGUIDAS=0
for i in 1 2 3 4 5 6 7 8 9; do
	QUIEN=$(instancia_que_responde "$ANA")
	printf '   consulta %d  X-Instancia: %s  (%s)\n' "$i" "$QUIEN" "$(nombre_de "$QUIEN")"
	[ "$QUIEN" = "$ANTERIOR" ] && SEGUIDAS=$((SEGUIDAS + 1))
	ANTERIOR=$QUIEN; VISTAS="$VISTAS $QUIEN"
done
DISTINTAS=$(printf '%s\n' $VISTAS | sort -u | wc -l | tr -d ' ')
nota "$DISTINTAS replicas distintas en 9 consultas; $SEGUIDAS veces se repitio la misma dos veces seguidas"
nota "el cliente no sabe cuantas replicas hay ni donde estan: solo conoce el 8080"

paso "9. Los endpoints internos no tienen puerta"
post "$PUERTA/cuentas/$ANA/abonos" '{"monto":1000000}'
nota "404: el gateway no tiene ruta para /cargos ni /abonos. Adentro de la red siguen abiertos (seguridad: mas adelante)"

paso "10. Errores de negocio, por la puerta, con el mismo codigo que dio cuenta"
post "$PUERTA/transferencias" "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$NADIE\",\"monto\":10}"
post "$PUERTA/transferencias" "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$BETO\",\"monto\":99999}"

paso "11. Los saldos no se movieron con los errores"
SA=$(saldo_de "$ANA"); SB=$(saldo_de "$BETO")
nota "Ana $SA (esperado 750) + Beto $SB (esperado 700) = $(suma "$SA" "$SB"); SPEI se llevo 50 a otro banco"

if [ "$MODO" != "--latido" ]; then
	printf '\n%sFin.%s Para ver cuanto tarda el directorio en enterarse de una caida: ./demo-v06.sh --latido\n' "$NEGRITA" "$NORMAL"
	exit 0
fi

# ---------------------------------------------------------------------------------------------
paso "12. El latido: matar una replica de cuenta sin que se despida"
VICTIMA=$(instancia_que_responde "$ANA")
NOMBRE=$(nombre_de "$VICTIMA")
if [ -z "$NOMBRE" ]; then printf 'No encontre el contenedor %s\n' "$VICTIMA"; exit 1; fi
nota "en el directorio hay $(instancias_en_eureka CUENTA) instancias de CUENTA; la victima es $VICTIMA ($NOMBRE)"
nota "docker kill = se va la luz: el proceso no alcanza a darse de baja. Solo dejan de llegar sus latidos."
nota "(docker stop seria un apagado ordenado: Spring la marca DOWN al instante y nadie le manda trafico)"
docker kill "$NOMBRE" >/dev/null
T0=$(date +%s)
BIEN=0; MAL=0; ULTIMO_MAL=0
while :; do
	AHORA=$(( $(date +%s) - T0 ))
	CUANTAS=$(instancias_en_eureka CUENTA)
	CODIGO=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$PUERTA/cuentas/$ANA")
	if [ "$CODIGO" = "200" ]; then BIEN=$((BIEN + 1)); else MAL=$((MAL + 1)); ULTIMO_MAL=$AHORA; fi
	[ $((AHORA % 5)) -eq 0 ] && printf '   t=%3ds  CUENTA en el directorio: %s   por la puerta: %d bien, %d con error\n' "$AHORA" "$CUANTAS" "$BIEN" "$MAL"
	if [ "$CUANTAS" -le 2 ]; then break; fi
	if [ "$AHORA" -gt 400 ]; then printf 'Sigue registrada despues de 400 s (autopreservacion activa?)\n'; break; fi
	sleep 1
done
BAJA=$(( $(date +%s) - T0 ))
nota "el directorio saco a la replica muerta a los $BAJA s"
nota "mientras tanto, por la puerta: $BIEN respuestas bien y $MAL con error (el ultimo error a los $ULTIMO_MAL s)"
nota "los errores son el gateway mandando peticiones a una replica que ya no existe: el registro es eventualmente consistente"
printf '   Lo que sabe ahora el directorio:\n'; registro

paso "13. Volver a prender la replica y ver cuanto tarda en reaparecer"
docker start "$NOMBRE" >/dev/null
T1=$(date +%s)
until [ "$(instancias_en_eureka CUENTA)" -ge 3 ]; do
	if [ $(( $(date +%s) - T1 )) -gt 300 ]; then printf 'No reaparecio en 300 s\n'; break; fi
	sleep 1
done
ALTA=$(( $(date +%s) - T1 ))
nota "$NOMBRE aparece otra vez en el directorio a los $ALTA s de docker start (arranque de la JVM + registro)"
until [ "$(instancia_que_responde "$ANA")" = "$VICTIMA" ]; do
	if [ $(( $(date +%s) - T1 )) -gt 300 ]; then printf 'El gateway no la uso en 300 s\n'; break; fi
	sleep 1
done
nota "y el gateway le vuelve a mandar peticiones a los $(( $(date +%s) - T1 )) s"
VALORES=$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$NOMBRE" | grep -E '^EUREKA_(LATIDO|EXPIRA|CONSULTA)_S=|^BALANCEADOR_CACHE=' | tr '\n' ' ')
printf '\n%sFin.%s Con %s(Spring Cloud trae latido 30, expiracion 90, consulta 30 y cache 35s).\n' "$NEGRITA" "$NORMAL" "$VALORES"
