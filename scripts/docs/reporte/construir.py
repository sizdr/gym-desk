"""Arma el reporte técnico en PDF a partir de docs/reporte/reporte.html.

Necesita Chrome (o Chromium) y pdftotext (poppler). Lo llama generar-reporte.sh con el nombre de un contenedor de PostgreSQL que ya tiene cargados
sql/01_esquema.sql, 02_datos.sql, 04_funciones.sql y 05_procedimientos.sql. Este script:

  1. corre cada consulta de sql/03_consultas.sql, sql/06_pruebas.sql y el borrado que rechaza el trigger,
     y guarda lo que devuelve la base (los resultados del reporte son reales, no copiados a mano);
  2. pone en la plantilla esos resultados, el diccionario de datos (docs/diccionario-de-datos.md), las
     evidencias de la app (docs/reporte/evidencias.md) y los SVG;
  3. imprime el HTML a PDF con Chrome sin interfaz, busca en qué página quedó cada título y vuelve a
     imprimir con el índice numerado.

Uso: python3 -I scripts/docs/reporte/construir.py CONTENEDOR ZONA_HORARIA SALIDA.pdf
"""
import csv
import html
import io
import re
import shutil
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[3]
FUENTE = RAIZ / "docs" / "reporte"
TRABAJO = RAIZ / "target" / "reporte" / "pdf"
FILAS_MAX = 12


# ---------- Base de datos ----------

def psql(contenedor, zona, sql, formato_csv=True):
    """Corre SQL en el contenedor y devuelve (salida, código)."""
    orden = ["docker", "exec", "-i", "-e", f"PGTZ={zona}", contenedor, "psql", "-q", "-U", "gimnasio", "-d",
             "gimnasio", "-v", "ON_ERROR_STOP=1"]
    if formato_csv:
        orden.append("--csv")
    proceso = subprocess.run(orden, input=sql, capture_output=True, text=True)
    return proceso.stdout + proceso.stderr, proceso.returncode


def consultas_del_archivo():
    """Las consultas de 03_consultas.sql con su id, recurso, necesidad y SQL."""
    texto = (RAIZ / "sql" / "03_consultas.sql").read_text(encoding="utf-8")
    consultas, actual = [], None
    for linea in texto.splitlines():
        cabecera = re.match(r"^-- (Q\d\d) · ([^·]+) · (.+)$", linea)
        if cabecera:
            actual = {"id": cabecera[1], "recurso": cabecera[2].strip(), "necesidad": cabecera[3].strip(), "sql": []}
            consultas.append(actual)
        elif actual is not None and linea.startswith("--") and not actual["sql"]:
            actual["necesidad"] += " " + linea[2:].strip()
        elif actual is not None and linea.strip():
            actual["sql"].append(linea)
    for consulta in consultas:
        consulta["sql"] = "\n".join(consulta["sql"]).strip()
    return consultas


def tabla_csv(salida, filas_max=FILAS_MAX):
    filas = list(csv.reader(io.StringIO(salida)))
    if not filas:
        return "<p class='nota'>Sin filas.</p>"
    cabecera, cuerpo = filas[0], filas[1:]
    partes = ["<table class='resultado'><thead><tr>"]
    partes += [f"<th>{html.escape(c)}</th>" for c in cabecera]
    partes.append("</tr></thead><tbody>")
    for fila in cuerpo[:filas_max]:
        partes.append("<tr>" + "".join(f"<td>{html.escape(v)}</td>" for v in fila) + "</tr>")
    partes.append("</tbody></table>")
    resto = len(cuerpo) - filas_max
    total = f"{len(cuerpo)} fila" + ("" if len(cuerpo) == 1 else "s")
    nota = f"{total}; se muestran las primeras {filas_max}." if resto > 0 else f"{total}."
    partes.append(f"<p class='pie-tabla'>{nota}</p>")
    return "".join(partes)


def anexo_consultas(contenedor, zona):
    bloques = []
    for consulta in consultas_del_archivo():
        salida, codigo = psql(contenedor, zona, consulta["sql"])
        if codigo != 0:
            raise SystemExit(f"{consulta['id']} falló:\n{salida}")
        bloques.append(
            f"<section class='consulta' id='consulta-{consulta['id'].lower()}'>"
            f"<h3>{consulta['id']} · {html.escape(consulta['recurso'])}</h3>"
            f"<p><strong>Necesidad:</strong> {html.escape(consulta['necesidad'])}</p>"
            f"<pre class='codigo'><code>{html.escape(consulta['sql'])}</code></pre>"
            f"<p><strong>Resultado con los datos de prueba:</strong></p>{tabla_csv(salida)}</section>")
    return "\n".join(bloques)


def salida_pruebas(contenedor, zona):
    salida, codigo = psql(contenedor, zona, (RAIZ / "sql" / "06_pruebas.sql").read_text(encoding="utf-8"),
                          formato_csv=False)
    if codigo != 0:
        raise SystemExit(f"06_pruebas.sql falló:\n{salida}")
    borrado, codigo = psql(contenedor, zona, "DELETE FROM pago WHERE folio = 'GYM-0001';", formato_csv=False)
    if codigo == 0:
        raise SystemExit("El trigger no rechazó el borrado de un cobro.")
    salida = re.sub(r"psql:<stdin>:\d+: ", "", salida)
    borrado = re.sub(r"psql:<stdin>:\d+: ", "", borrado).strip()
    return (f"<pre class='salida'>{html.escape(salida.strip())}</pre>"
            f"<p><strong>T01.</strong> <code>DELETE FROM pago WHERE folio = 'GYM-0001';</code> devuelve:</p>"
            f"<pre class='salida'>{html.escape(borrado)}</pre>")


# ---------- Markdown sencillo (diccionario y evidencias) ----------

def en_linea(texto):
    texto = html.escape(texto)
    texto = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", texto)
    texto = re.sub(r"`([^`]+)`", r"<code>\1</code>", texto)
    texto = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", texto)
    return texto


def markdown(texto, nivel_base=3, prefijo_id=""):
    """Títulos, párrafos, tablas y bloques de código; los títulos bajan a h{nivel_base} y siguientes."""
    salida, lineas, i = [], texto.splitlines(), 0
    while i < len(lineas):
        linea = lineas[i]
        titulo = re.match(r"^(#+) (.+)$", linea)
        if titulo:
            nivel = min(6, nivel_base + len(titulo[1]) - 2)
            if len(titulo[1]) == 1:
                i += 1
                continue
            ident = prefijo_id + re.sub(r"[^a-z0-9]+", "-", titulo[2].lower()).strip("-")
            salida.append(f"<h{nivel} id='{ident}'>{en_linea(titulo[2])}</h{nivel}>")
        elif linea.startswith("```"):
            bloque = []
            i += 1
            while i < len(lineas) and not lineas[i].startswith("```"):
                bloque.append(lineas[i])
                i += 1
            salida.append(f"<pre class='codigo'><code>{html.escape(chr(10).join(bloque))}</code></pre>")
        elif linea.startswith("|"):
            filas = []
            while i < len(lineas) and lineas[i].startswith("|"):
                celdas = [c.strip() for c in lineas[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", c) for c in celdas):
                    filas.append(celdas)
                i += 1
            i -= 1
            partes = ["<table class='datos'><thead><tr>"]
            partes += [f"<th>{en_linea(c)}</th>" for c in filas[0]]
            partes.append("</tr></thead><tbody>")
            for fila in filas[1:]:
                partes.append("<tr>" + "".join(f"<td>{en_linea(c)}</td>" for c in fila) + "</tr>")
            partes.append("</tbody></table>")
            salida.append("".join(partes))
        elif linea.strip():
            parrafo = [linea]
            while i + 1 < len(lineas) and lineas[i + 1].strip() and not re.match(r"^(#|\||```)", lineas[i + 1]):
                i += 1
                parrafo.append(lineas[i])
            salida.append(f"<p>{en_linea(' '.join(parrafo))}</p>")
        i += 1
    return "\n".join(salida)


# ---------- Ensamblado e impresión ----------

def svg_en_linea(nombre):
    texto = (FUENTE / "img" / nombre).read_text(encoding="utf-8")
    return re.sub(r"<\?xml[^>]*>", "", texto)


def indice(html_texto, paginas=None):
    """Índice con los h2 y h3 numerados del cuerpo (los que llevan data-indice)."""
    entradas = re.findall(r"<(h[23])[^>]*id=['\"]([^'\"]+)['\"][^>]*data-indice[^>]*>(.*?)</h[23]>", html_texto, re.S)
    filas = []
    for etiqueta, ident, texto in entradas:
        limpio = re.sub(r"<[^>]+>", "", texto).strip()
        pagina = (paginas or {}).get(ident, "")
        filas.append(f"<li class='indice-{etiqueta}'><a href='#{ident}'><span>{limpio}</span>"
                     f"<span class='relleno'></span><span class='pagina'>{pagina}</span></a></li>")
    return "<ol class='indice'>" + "".join(filas) + "</ol>", entradas


def imprimir(html_ruta, pdf_ruta):
    chrome = shutil.which("google-chrome-stable") or shutil.which("google-chrome") or shutil.which("chromium")
    if not chrome:
        raise SystemExit("Falta Chrome o Chromium para imprimir el PDF.")
    subprocess.run([chrome, "--headless=new", "--disable-gpu", "--no-pdf-header-footer", "--run-all-compositor-stages-before-draw",
                    "--virtual-time-budget=10000", f"--print-to-pdf={pdf_ruta}", html_ruta.as_uri()],
                   check=True, capture_output=True)


def paginas_de_titulos(pdf_ruta, entradas):
    # pdftotext (poppler) separa las páginas con un salto de página (\f).
    texto = subprocess.run(["pdftotext", "-enc", "UTF-8", str(pdf_ruta), "-"], check=True, capture_output=True,
                           text=True).stdout
    textos = [re.sub(r"\s+", " ", pagina) for pagina in texto.split("\f")]
    if textos and not textos[-1].strip():
        textos.pop()
    # Cada título aparece primero en el propio índice: se busca en el cuerpo, después de la página donde el
    # índice termina (la que trae su última entrada).
    def limpiar(t):
        return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", t))).strip()
    ultima = limpiar(entradas[-1][2])
    fin_indice = next((n for n, pagina in enumerate(textos) if ultima in pagina), 0)
    resultado, desde = {}, fin_indice + 1
    for _, ident, texto in entradas:
        limpio = re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", "", texto))).strip()
        for n in range(desde, len(textos)):
            if limpio in textos[n]:
                resultado[ident] = n + 1
                desde = n
                break
    return resultado, len(textos)


def main():
    contenedor, zona, salida_pdf = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    if TRABAJO.exists():
        shutil.rmtree(TRABAJO)
    shutil.copytree(FUENTE / "img", TRABAJO / "img")
    shutil.copy(FUENTE / "reporte.css", TRABAJO / "reporte.css")
    shutil.copy(RAIZ / "docs" / "diagrama-modelo-de-datos.svg", TRABAJO / "img" / "diagrama-modelo-de-datos.svg")
    (TRABAJO / "fuentes").mkdir()
    for fuente in (RAIZ / "src" / "main" / "resources" / "fonts").glob("*.ttf"):
        shutil.copy(fuente, TRABAJO / "fuentes" / fuente.name)

    plantilla = (FUENTE / "reporte.html").read_text(encoding="utf-8")
    diccionario = (RAIZ / "docs" / "diccionario-de-datos.md").read_text(encoding="utf-8")
    evidencias = (FUENTE / "evidencias.md").read_text(encoding="utf-8")
    reemplazos = {
        "<!--CONSULTAS-->": anexo_consultas(contenedor, zona),
        "<!--PRUEBAS-SQL-->": salida_pruebas(contenedor, zona),
        "<!--DICCIONARIO-->": markdown(diccionario, nivel_base=3, prefijo_id="dic-"),
        "<!--EVIDENCIAS-APP-->": markdown(evidencias, nivel_base=3, prefijo_id="evi-"),
        "<!--SVG:arquitectura-->": svg_en_linea("arquitectura.svg"),
    }
    # <!--ARCHIVO:ruta--> pone el archivo tal cual, para los anexos de código.
    for ruta in re.findall(r"<!--ARCHIVO:([^>]+)-->", plantilla):
        codigo = (RAIZ / ruta).read_text(encoding="utf-8").strip()
        plantilla = plantilla.replace(f"<!--ARCHIVO:{ruta}-->",
                                      f"<pre class='codigo'><code>{html.escape(codigo)}</code></pre>")
    for marca, contenido in reemplazos.items():
        if marca not in plantilla:
            raise SystemExit(f"La plantilla no tiene {marca}")
        plantilla = plantilla.replace(marca, contenido)

    html_ruta = TRABAJO / "reporte.html"
    pdf_ruta = TRABAJO / "reporte.pdf"
    tabla, entradas = indice(plantilla)
    html_ruta.write_text(plantilla.replace("<!--INDICE-->", tabla), encoding="utf-8")
    imprimir(html_ruta, pdf_ruta)
    paginas, total = paginas_de_titulos(pdf_ruta, entradas)
    faltan = [ident for _, ident, _ in entradas if ident not in paginas]
    if faltan:
        print("Aviso: no se encontró la página de " + ", ".join(faltan), file=sys.stderr)
    tabla, _ = indice(plantilla, paginas)
    html_ruta.write_text(plantilla.replace("<!--INDICE-->", tabla), encoding="utf-8")
    imprimir(html_ruta, pdf_ruta)
    salida_pdf.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(pdf_ruta, salida_pdf)
    print(f"Listo: {salida_pdf} ({total} páginas)")


if __name__ == "__main__":
    main()
