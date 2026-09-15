# Guía para agentes en mexi-banco

Este archivo lo leen Claude Code, Codex y Hermes. Las personas del equipo son responsables de lo que un agente produce.

## Qué es este proyecto

Mexi Banco es el caso hilo conductor del curso de Sistemas Distribuidos (FI-UNAM). Hoy es un
monolito de Spring Boot con cuenta, movimiento, transferencia, SPEI y notificación, que se va a
partir en piezas sesión a sesión. Hay una etiqueta de git por sesión (`v05`, `v06a`, `v06`, ...) y
el plan completo está en el Roadmap del `README.md`. No adelantar una pieza antes de su sesión.

## Cómo se construye y se prueba

```bash
docker compose up --build   # Postgres y la app; health en http://localhost:8080/actuator/health
./mvnw test                 # pruebas (hoy no hay ninguna en src/test)
```

## Convenciones

- Código, commits y documentación en español, sin guiones largos.
- Conventional Commits con alcance y número de issue: `feat(modulo): qué (#12)`.
- (Lo que difiera de los defaults del lenguaje, con un ejemplo por convención.)

## Prohibiciones

- Nunca push a `main`; nunca comandos destructivos de barrido; nunca secretos ni datos reales en el repo.
- Nunca subir `referencias/`: es material de DevTalles con derechos de autor y el repo es público.
- Nunca reescribir ni mover una etiqueta ya publicada; una sesión nueva es una etiqueta nueva.
- Nombres de alumnos, matrículas o calificaciones jamás, ni en código, commits, issues o ejemplos.

## Fábrica

Este repositorio sigue el proceso de la fábrica de agentes de J3L (`~/fabrica`, `procesos/`). El contrato del proyecto está en `docs/agents/proyecto.md` (modo, tracker, etiquetas, dónde vive el contexto). Antes de tomar un issue: `~/fabrica/scripts/sincronizar.sh`, luego `~/fabrica/scripts/reclamar.sh <numero> <tipo>`. Lee `docs/contexto/CONTEXT.md` antes de decidir; no lo edites, escribe una entrada en `docs/contexto/entradas/`.
