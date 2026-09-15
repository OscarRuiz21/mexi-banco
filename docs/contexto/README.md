# Contexto

- `entradas/`: una entrada por PR que cambie dominio, decisiones, arquitectura, glosario u operación. Se crea con `~/fabrica/scripts/contexto-nueva-entrada.sh <issue> "<título>" [afecta]`.
- `CONTEXT.md`: generado en `main` por el workflow `contexto.yml` con `generar.py` (copia de `fabrica/scripts/contexto-generar.py`; la fábrica lo actualiza por PR). No se edita a mano. Reconstruir: `python3 docs/contexto/generar.py .`
- `resumen.md` (opcional): estado vigente consolidado, se actualiza por PR (skill `contexto-consolidar`).

Formato y reglas en `~/fabrica/procesos/contexto.md`.
