# Mexi Banco

Caso docente de Sistemas Distribuidos (FI-UNAM, 2027-1): un banco ficticio con cinco módulos
(cuenta, movimiento, transferencia, notificación y SPEI) que empieza como monolito y se va
partiendo en servicios durante el semestre. Cada etapa vive en su propia rama, con su README.

## Etapas

| Etapa | Rama | Qué es | Estado |
|---|---|---|---|
| 01 | `01-monolito` | El monolito: un proceso Java y Postgres. | Disponible |
| 02 | `02-separacion` | Cinco servicios, cada uno con su base, que se llaman por HTTP. | Disponible |
| Lab 03 | `lab/03-gateway-discovery-balanceo` | Base del laboratorio de la S07: discovery y gateway por completar (`TODO H1`, `H2`, `H3`). | Disponible |
| 03 resuelta | `03-gateway-discovery-balanceo` | Gateway, discovery y balanceo resueltos, para comparar. | Se publica al cierre del lab de la S07 |

Para clonar una etapa, cambia `<rama>` por la de la tabla:

```bash
git clone --branch <rama> https://github.com/OscarRuiz21/mexi-banco.git
```

Por ejemplo, `git clone --branch 02-separacion https://github.com/OscarRuiz21/mexi-banco.git`.
Necesitas Docker con Compose; cada rama explica cómo levantarla y qué comprobar.

## Compatibilidad

Las etiquetas `v05` y `v05.1` se conservan. La guía del laboratorio de la S05 sigue funcionando
con `git clone --branch v05.1 https://github.com/OscarRuiz21/mexi-banco.git`.

## Nombres históricos

Algunos nombres internos conservan su denominación anterior a las ramas numeradas: scripts como
`demo-v06a.sh` y `demo-v06.sh`, y proyectos de Compose, redes, imágenes y contenedores como
`mexi-banco-v06-...`. No son etiquetas de git ni indican que debas cambiar de rama.
