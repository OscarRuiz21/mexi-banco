#!/usr/bin/env bash
# Demo de Mexi Banco v06a: el monolito partido en cinco servicios.
#
# Uso (con todo arriba y healthy: docker compose up --build -d ; docker compose ps):
#   ./demo-v06a.sh          flujo feliz + errores de negocio propagados entre servicios
#   ./demo-v06a.sh --roto   ademas, "lo que se rompio al partir": un cargo sin su abono
#
# No borra nada. En modo --roto apaga y vuelve a prender el contenedor `cuenta` (stop/start) y
# recrea `transferencia` dos veces para activar y desactivar la pausa de demo.
# Las CLABEs cambian en cada corrida, asi que se puede correr varias veces seguidas.

set -u
MODO="${1:-}"
cd "$(dirname "$0")"

CUENTA=http://localhost:8081
MOVIMIENTO=http://localhost:8082
TRANSFERENCIA=http://localhost:8083
NOTIFICACION=http://localhost:8084
SPEI=http://localhost:8085

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
			*[!A-Za-z0-9_./:=-]*) linea="$linea '$arg'" ;;
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
saldo_de() { CUERPO=$(curl -s "$CUENTA/cuentas/$1"); campo saldo; }

esperar_sano() {
	local servicio=$1 intentos=0
	printf 'Esperando a que %s este healthy' "$servicio"
	until [ "$(docker compose ps --format '{{.Health}}' "$servicio" 2>/dev/null)" = "healthy" ]; do
		intentos=$((intentos + 1))
		if [ $intentos -gt 60 ]; then printf ' (no llego en 120 s)\n'; return 1; fi
		printf '.'; sleep 2
	done
	printf ' listo\n'
}

# ---------------------------------------------------------------------------------------------
paso "0. Cinco servicios, cinco puertos (el cliente tiene que sabérselos todos)"
for par in "cuenta $CUENTA" "movimiento $MOVIMIENTO" "transferencia $TRANSFERENCIA" "notificacion $NOTIFICACION" "spei $SPEI"; do
	set -- $par
	printf '%-14s %s/actuator/health -> %s\n' "$1" "$2" "$(curl -s "$2/actuator/health")"
done

paso "1. Abrir la cuenta de Ana con 1000 (servicio cuenta, puerto 8081)"
post "$CUENTA/cuentas" "{\"clabe\":\"$ANA\",\"titular\":\"Ana\",\"saldoInicial\":1000}"
nota "cuenta llamo por HTTP a movimiento para asentar el saldo inicial"

paso "2. Abrir la cuenta de Beto con 500"
post "$CUENTA/cuentas" "{\"clabe\":\"$BETO\",\"titular\":\"Beto\",\"saldoInicial\":500}"

paso "3. Transferir 200 de Ana a Beto (servicio transferencia, puerto 8083)"
post "$TRANSFERENCIA/transferencias" "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$BETO\",\"monto\":200}"
ID_TRANSFERENCIA=$(campo id)
nota "transferencia hizo 8 llamadas HTTP: 2 consultas, cargo y abono a cuenta, 2 asientos a movimiento, 2 avisos a notificacion"

paso "4. Consultar esa transferencia"
pedir "$TRANSFERENCIA/transferencias/$ID_TRANSFERENCIA"

paso "5. Saldos despues de la transferencia (servicio cuenta)"
pedir "$CUENTA/cuentas/$ANA"
pedir "$CUENTA/cuentas/$BETO"
SA=$(saldo_de "$ANA"); SB=$(saldo_de "$BETO")
nota "Ana $SA + Beto $SB = $(suma "$SA" "$SB") (antes: 1000 + 500 = 1500). Esperado: 800 y 700"

paso "6. Estado de cuenta de Ana (servicio movimiento, puerto 8082)"
pedir "$MOVIMIENTO/movimientos?clabe=$ANA"

paso "7. Avisos que recibio Beto (servicio notificacion, puerto 8084)"
pedir "$NOTIFICACION/notificaciones?clabe=$BETO"

paso "8. SPEI de 50 desde Ana a otro banco, con Idempotency-Key (servicio spei, puerto 8085)"
post "$SPEI/spei" "{\"claveOrigen\":\"$ANA\",\"bancoDestino\":\"BANCO-X\",\"claveDestino\":\"012180000000000009\",\"monto\":50}" -H "Idempotency-Key: $LLAVE"
ID_SPEI=$(campo id)

paso "9. El mismo SPEI otra vez, MISMA Idempotency-Key (un reintento del cliente)"
post "$SPEI/spei" "{\"claveOrigen\":\"$ANA\",\"bancoDestino\":\"BANCO-X\",\"claveDestino\":\"012180000000000009\",\"monto\":50}" -H "Idempotency-Key: $LLAVE"
nota "mismo id ($ID_SPEI -> $(campo id)): no se cobro dos veces"

paso "10. Saldo de Ana despues del SPEI y su repeticion"
pedir "$CUENTA/cuentas/$ANA"
nota "esperado 750 (800 - 50, una sola vez)"

# ---------------------------------------------------------------------------------------------
paso "11. Error: transferir a una CLABE que no existe"
post "$TRANSFERENCIA/transferencias" "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$NADIE\",\"monto\":10}"
nota "el 404 lo dijo cuenta; transferencia lo propaga igual (\"servicio\": \"cuenta\"), no como 500"

paso "12. Error: transferir mas de lo que hay"
post "$TRANSFERENCIA/transferencias" "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$BETO\",\"monto\":99999}"
nota "422 de cuenta, propagado por transferencia"

paso "13. Error: SPEI sin Idempotency-Key"
post "$SPEI/spei" "{\"claveOrigen\":\"$ANA\",\"bancoDestino\":\"BANCO-X\",\"claveDestino\":\"012180000000000009\",\"monto\":50}"

paso "14. Error: abrir otra vez la cuenta de Ana"
post "$CUENTA/cuentas" "{\"clabe\":\"$ANA\",\"titular\":\"Ana\",\"saldoInicial\":1}"

paso "15. Los saldos no se movieron con los errores"
nota "Ana $(saldo_de "$ANA") (esperado 750), Beto $(saldo_de "$BETO") (esperado 700)"

if [ "$MODO" != "--roto" ]; then
	printf '\n%sFin del flujo feliz.%s Para ver lo que se rompio al partir: ./demo-v06a.sh --roto\n' "$NEGRITA" "$NORMAL"
	exit 0
fi

# ---------------------------------------------------------------------------------------------
paso "16. Lo que se rompio: activar una pausa de 8 s entre el cargo y el abono"
nota "solo para la demo; recrea el contenedor transferencia con PAUSA_ENTRE_CARGO_Y_ABONO_MS=8000"
PAUSA_ENTRE_CARGO_Y_ABONO_MS=8000 docker compose up -d transferencia
esperar_sano transferencia

ANTES_A=$(saldo_de "$ANA"); ANTES_B=$(saldo_de "$BETO")
paso "17. Transferir 100 de Ana a Beto y, a media transferencia, apagar cuenta"
nota "antes: Ana $ANTES_A, Beto $ANTES_B, suma $(suma "$ANTES_A" "$ANTES_B")"
RESPUESTA=$(mktemp)
curl -s -w '\n[HTTP %{http_code}]\n' -X POST "$TRANSFERENCIA/transferencias" -H 'Content-Type: application/json' \
	-d "{\"claveOrigen\":\"$ANA\",\"claveDestino\":\"$BETO\",\"monto\":100}" > "$RESPUESTA" &
CURL_PID=$!
sleep 3
nota "el cargo ya se confirmo en cuenta; ahora se cae cuenta antes del abono"
docker compose stop cuenta
wait $CURL_PID
nota "respuesta de transferencia:"
cat "$RESPUESTA"; rm -f "$RESPUESTA"

paso "18. Prender cuenta otra vez y ver los saldos"
docker compose start cuenta
esperar_sano cuenta
DESPUES_A=$(saldo_de "$ANA"); DESPUES_B=$(saldo_de "$BETO")
pedir "$CUENTA/cuentas/$ANA"
pedir "$CUENTA/cuentas/$BETO"
nota "antes: $ANTES_A + $ANTES_B = $(suma "$ANTES_A" "$ANTES_B")"
nota "ahora: $DESPUES_A + $DESPUES_B = $(suma "$DESPUES_A" "$DESPUES_B")"
nota "a Ana se le cobraron 100 que Beto nunca recibio: el dinero quedo en el aire"
pedir "$MOVIMIENTO/movimientos?clabe=$ANA"
nota "y el estado de cuenta de Ana no dice nada de ese cargo: el saldo y el ledger ya no cuadran"

paso "19. Quitar la pausa de demo (transferencia vuelve a la normalidad)"
docker compose up -d transferencia
esperar_sano transferencia
printf '\n%sFin.%s En v05.1 esto era un rollback; aqui ya no hay transaccion que lo deshaga. Eso es la S12 (sagas).\n' "$NEGRITA" "$NORMAL"
