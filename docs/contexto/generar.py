#!/usr/bin/env python3
"""Genera docs/contexto/CONTEXT.md a partir de docs/contexto/entradas/*.md. Determinista, sin dependencias.
Uso: contexto-generar.py [ruta-del-proyecto]   (por defecto, el directorio actual)
Frontmatter de cada entrada: fecha, issue, pr, agente, modo, afecta (lista), reemplaza (lista), estado (propuesta | aceptada; sin el campo = aceptada). Opcionales: ambito (p. ej. sd | mpoo | todas) y tema (clave jerárquica, p. ej. calendario.s05): dos entradas aceptadas y vigentes con el mismo ambito+tema son una contradicción y el generador sale con error.
Valida y avisa por stderr; sale con 1 si alguna entrada es inválida (el CONTEXT.md se escribe con las válidas)."""
import re, sys, pathlib

SECCIONES = ["dominio", "decisiones", "arquitectura", "glosario", "operacion", "intentos-fallidos"]
TITULOS = {"dominio": "Dominio", "decisiones": "Decisiones", "arquitectura": "Arquitectura",
           "glosario": "Glosario", "operacion": "Operación", "intentos-fallidos": "Intentos fallidos"}

def lista(v):
    if isinstance(v, list):
        return v
    v = (v or "").strip()
    if v.startswith("[") and v.endswith("]"):
        return [x.strip().strip("'\"") for x in v[1:-1].split(",") if x.strip()]
    return [v] if v else []

def frontmatter(texto):
    m = re.match(r"^---\n(.*?)\n---\n?(.*)$", texto, re.S)
    if not m:
        return None, texto
    meta, clave = {}, None
    for linea in m.group(1).splitlines():
        if re.match(r"^\s+-\s+", linea) and clave:            # lista YAML en bloque
            meta.setdefault(clave, [])
            if not isinstance(meta[clave], list):
                meta[clave] = lista(meta[clave])
            meta[clave].append(linea.split("-", 1)[1].strip().strip("'\""))
            continue
        if ":" not in linea:
            continue
        k, v = linea.split(":", 1)
        clave = k.strip()
        meta[clave] = v.strip()
    return meta, m.group(2)

def main():
    raiz = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    carpeta = raiz / "docs" / "contexto"
    entradas_dir = carpeta / "entradas"
    if not entradas_dir.is_dir():
        print(f"ERROR: no existe {entradas_dir}; ¿es la raíz del proyecto?", file=sys.stderr)
        sys.exit(1)
    errores, entradas = [], []
    for p in sorted(entradas_dir.glob("*.md")):
        meta, cuerpo = frontmatter(p.read_text(encoding="utf-8"))
        if meta is None:
            errores.append(f"{p.name}: sin frontmatter (--- ... ---)"); continue
        afecta = [a.lower() for a in lista(meta.get("afecta"))]
        malos = [a for a in afecta if a not in SECCIONES]
        if not afecta:
            errores.append(f"{p.name}: falta 'afecta'"); continue
        if malos:
            errores.append(f"{p.name}: 'afecta' con valores desconocidos {malos}; válidos: {SECCIONES}"); continue
        if not meta.get("fecha"):
            errores.append(f"{p.name}: falta 'fecha'"); continue
        meta["afecta"] = afecta
        meta["reemplaza"] = lista(meta.get("reemplaza"))
        estado = (meta.get("estado") or "aceptada").strip().lower()
        if estado not in ("propuesta", "aceptada"):
            errores.append(f"{p.name}: 'estado' debe ser propuesta o aceptada (tiene '{estado}')"); continue
        meta["estado"] = estado
        lineas = cuerpo.splitlines()
        titulo, resto, quitado = p.stem, [], False
        for l in lineas:
            if not quitado and l.startswith("# "):
                titulo, quitado = l[2:].strip(), True
                continue
            resto.append(l)
        entradas.append({"id": p.stem, "meta": meta, "titulo": titulo, "cuerpo": "\n".join(resto).strip()})
    ids = {e["id"] for e in entradas}
    reemplazadas = {}
    for e in entradas:
        for r in e["meta"]["reemplaza"]:
            if r not in ids:
                errores.append(f"{e['id']}.md: 'reemplaza' apunta a '{r}', que no existe")
                continue
            otro = next(x for x in entradas if x["id"] == r)
            if e["id"] in otro["meta"]["reemplaza"]:
                errores.append(f"{e['id']}.md y {r}.md se reemplazan mutuamente; quita uno de los dos")
                continue
            reemplazadas[r] = e["id"]
    salida = ["<!-- GENERADO por fabrica/scripts/contexto-generar.py. No editar a mano: escribe una entrada en entradas/. -->",
              "# Contexto del proyecto", ""]
    resumen = carpeta / "resumen.md"
    if resumen.exists():
        salida += ["## Estado vigente (resumen consolidado)", "", resumen.read_text(encoding="utf-8").strip(), ""]
    propuestas = [e for e in entradas if e["meta"]["estado"] == "propuesta"]
    vigentes = [e for e in entradas if e["meta"]["estado"] == "aceptada" and e["id"] not in reemplazadas]
    por_tema = {}
    for e in vigentes:
        tema = (e["meta"].get("tema") or "").strip()
        if tema:
            por_tema.setdefault(((e["meta"].get("ambito") or "").strip(), tema), []).append(e["id"])
    for (ambito, tema), ids in sorted(por_tema.items()):
        if len(ids) > 1:
            errores.append(f"contradicción: {len(ids)} entradas aceptadas y vigentes sobre {ambito + ' ' if ambito else ''}{tema}: {', '.join(ids)}; una debe reemplazar a la otra")
    salida += [f"Entradas: {len(entradas)}. Vigentes: {len(vigentes)}. Propuestas pendientes de aceptar: {len(propuestas)}.", ""]
    if propuestas:
        salida += ["## Propuestas (no vigentes hasta que una persona las acepte)", ""]
        for e in sorted(propuestas, key=lambda e: (str(e["meta"].get("fecha", "")), e["id"])):
            m = e["meta"]
            salida += [f"- **{e['titulo']}** (`{e['id']}`, {m.get('fecha','')}, {m.get('agente','')})"]
        salida += [""]
    for s in SECCIONES:
        del_s = [e for e in entradas if s in e["meta"]["afecta"] and e["meta"]["estado"] == "aceptada"]
        if not del_s:
            continue
        salida += [f"## {TITULOS[s]}", ""]
        for e in sorted(del_s, key=lambda e: (str(e["meta"].get("fecha", "")), e["id"])):
            m = e["meta"]
            ref = " · ".join(x for x in [str(m.get("fecha", "")), f"#{m['issue']}" if m.get("issue") else "",
                                        f"PR #{m['pr']}" if m.get("pr") else "", str(m.get("agente", ""))] if x)
            if e["id"] in reemplazadas:
                salida += [f"### ~~{e['titulo']}~~ (reemplazada por `{reemplazadas[e['id']]}`)", "", f"_{ref}_", ""]
            else:
                salida += [f"### {e['titulo']}", "", f"_{ref}_", "", e["cuerpo"], ""]
    (carpeta / "CONTEXT.md").write_text("\n".join(salida).rstrip() + "\n", encoding="utf-8")
    for er in errores:
        print(f"ERROR entrada: {er}", file=sys.stderr)
    print(f"CONTEXT.md generado con {len(entradas)} entradas válidas" + (f" y {len(errores)} con error" if errores else ""))
    sys.exit(1 if errores else 0)

if __name__ == "__main__":
    main()
