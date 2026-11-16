"""Dibuja el diagrama del modelo de datos como SVG a partir del JSON que produce esquema.sql.

Lee el JSON de la entrada estándar y escribe el SVG en la salida estándar. La posición de cada tabla está en
LAYOUT; las columnas, los tipos, las claves y las relaciones salen de la base, así que el diagrama no se
desactualiza respecto de las migraciones. Lo ejecuta generar-diagrama.sh.
"""
import json
import sys
from html import escape

ANCHO = 270
FILA = 24
CABECERA = 34
MARGEN_INFERIOR = 8
SEPARACION = 64

AZUL, MORADO, GRIS, CAFE = "#3f6fb5", "#7b5ea7", "#8a8a8a", "#b07a2a"

# Tres columnas, como el diagrama del proyecto: socios y planes; cobros; usuarios, métodos y enumerados.
LAYOUT = [
    (40, [("socio", AZUL), ("plan_membresia", AZUL), ("configuracion", GRIS)]),
    (460, [("pago", AZUL), ("membresia", AZUL), ("folio_secuencia", GRIS)]),
    (880, [("usuario", MORADO), ("metodo_pago", MORADO), ("enum rol_usuario", CAFE), ("enum tipo_cobro", CAFE)]),
]


def alto(filas):
    return CABECERA + filas * FILA + MARGEN_INFERIOR


def caja(x, y, titulo, color, filas):
    """filas: lista de (nombre, tipo, pk, fk). Devuelve el SVG y la coordenada y del centro de cada fila."""
    h = alto(len(filas))
    out = [f'<g><rect x="{x + 2}" y="{y + 3}" width="{ANCHO}" height="{h}" rx="8" fill="#000" opacity="0.06"/>',
           f'<rect x="{x}" y="{y}" width="{ANCHO}" height="{h}" rx="8" fill="#ffffff" stroke="#d5dae1"/>',
           f'<path d="M{x},{y + CABECERA} V{y + 8} Q{x},{y} {x + 8},{y} H{x + ANCHO - 8} '
           f'Q{x + ANCHO},{y} {x + ANCHO},{y + 8} V{y + CABECERA} Z" fill="{color}"/>',
           f'<text x="{x + 12}" y="{y + 22}" font-size="14" font-weight="700" fill="#ffffff">{escape(titulo)}</text>']
    centros = {}
    for i, (nombre, tipo, pk, fk) in enumerate(filas):
        top = y + CABECERA + i * FILA
        if i % 2 == 1:
            out.append(f'<rect x="{x + 1}" y="{top}" width="{ANCHO - 2}" height="{FILA}" fill="#f8f9fb"/>')
        base = top + 17
        peso = ' font-weight="700"' if pk else ""
        out.append(f'<text x="{x + 12}" y="{base}" font-size="13" fill="#1f2a37"{peso}>{escape(nombre)}</text>')
        insignia_x = x + 12 + len(nombre) * 7.6 + 8
        for etiqueta, color_insignia, activa in (("PK", "#e8a33d", pk), ("FK", "#6b8fc9", fk)):
            if activa:
                out.append(f'<rect x="{insignia_x:.1f}" y="{top + 5}" width="22" height="14" rx="3" fill="{color_insignia}"/>')
                out.append(f'<text x="{insignia_x + 11:.1f}" y="{top + 15.5}" font-size="9" font-weight="700" '
                           f'fill="#fff" text-anchor="middle">{etiqueta}</text>')
                insignia_x += 26
        out.append(f'<text x="{x + ANCHO - 12}" y="{base}" font-size="12" fill="#7a8594" '
                   f'text-anchor="end">{escape(tipo)}</text>')
        centros[nombre] = top + FILA / 2
    out.append("</g>")
    return "\n".join(out), h, centros


def linea(x1, y1, x2, y2, etiqueta1, etiqueta2, rodeo=None):
    """Curva de x1,y1 (hija) a x2,y2 (padre) con su cardinalidad en cada extremo."""
    if rodeo is None:
        medio = (x1 + x2) / 2
        d = f"M{x1},{y1} C{medio},{y1} {medio},{y2} {x2},{y2}"
    else:
        d = f"M{x1},{y1} C{rodeo},{y1} {rodeo},{y2} {x2},{y2}"
    out = [f'<path d="{d}" fill="none" stroke="#9aa4b2" stroke-width="1.6"/>',
           f'<circle cx="{x1}" cy="{y1}" r="3" fill="#9aa4b2"/>',
           f'<circle cx="{x2}" cy="{y2}" r="3" fill="#9aa4b2"/>']
    for x, y, texto, lado in ((x1, y1, etiqueta1, x1 <= (rodeo if rodeo is not None else x2)),
                              (x2, y2, etiqueta2, x2 < (rodeo if rodeo is not None else x1))):
        tx = x + 8 if lado else x - 14
        out.append(f'<text x="{tx}" y="{y - 6}" font-size="13" font-weight="700" fill="#5b6675">{texto}</text>')
    return "\n".join(out)


def main():
    esquema = json.load(sys.stdin)
    tablas, enums, relaciones = esquema["tablas"], esquema.get("enums") or {}, esquema["relaciones"]
    dibujadas = {"enum " + nombre for nombre in enums} | set(tablas)
    en_layout = {titulo for _, cajas in LAYOUT for titulo, _ in cajas}
    faltan = dibujadas - en_layout
    if faltan:
        sys.exit("Faltan en LAYOUT: " + ", ".join(sorted(faltan)))

    cajas_svg, posiciones, abajo = [], {}, 0
    for x, cajas in LAYOUT:
        y = 70
        for titulo, color in cajas:
            if titulo not in dibujadas:
                continue
            if titulo.startswith("enum "):
                filas = [(valor, "", False, False) for valor in enums[titulo[5:]]]
            else:
                filas = [(c["nombre"], c["tipo"] + ("?" if c["nulo"] else ""), c["pk"], c["fk"])
                         for c in tablas[titulo]]
            svg, h, centros = caja(x, y, titulo, color, filas)
            cajas_svg.append(svg)
            posiciones[titulo] = (x, centros)
            y += h + SEPARACION
        abajo = max(abajo, y - SEPARACION)

    lineas = []
    for r in relaciones:
        hx, hc = posiciones[r["tabla"]]
        px, pc = posiciones[r["referencia"]]
        y1, y2 = hc[r["columna"]], pc[r["columna_referencia"]]
        hija, padre = ("1" if r["unica"] else "*"), "1"
        if r["tabla"] == r["referencia"]:
            # Referencia a la misma tabla: rodea por la derecha.
            lineas.append(linea(hx + ANCHO, y1, px + ANCHO, y2, hija, padre, rodeo=hx + ANCHO + 46))
        elif hx == px:
            # Misma columna: rodea por la izquierda.
            lineas.append(linea(hx, y1, px, y2, hija, padre, rodeo=hx - 60))
        elif px < hx:
            lineas.append(linea(hx, y1, px + ANCHO, y2, hija, padre))
        else:
            lineas.append(linea(hx + ANCHO, y1, px, y2, hija, padre))

    ancho_total = max(x for x, _ in LAYOUT) + ANCHO + 60
    alto_total = abajo + 60
    print(f'<svg xmlns="http://www.w3.org/2000/svg" width="{ancho_total}" height="{alto_total}" '
          f'viewBox="0 0 {ancho_total} {alto_total}" font-family="Inter, \'Segoe UI\', Helvetica, Arial, sans-serif">')
    print(f'<rect width="{ancho_total}" height="{alto_total}" fill="#f7f8fa"/>')
    print('<defs><pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">'
          '<circle cx="1" cy="1" r="1" fill="#dde1e7"/></pattern></defs>')
    print(f'<rect width="{ancho_total}" height="{alto_total}" fill="url(#grid)"/>')
    print('<text x="40" y="40" font-size="20" font-weight="700" fill="#1f2a37">'
          'Sistema de gestión de gimnasio · modelo de datos</text>')
    print("\n".join(lineas))
    print("\n".join(cajas_svg))
    print(f'<text x="40" y="{alto_total - 18}" font-size="12" fill="#5b6675">'
          'PK clave primaria · FK clave foránea · ? admite nulo · 1 / * cardinalidad</text>')
    print("</svg>")


if __name__ == "__main__":
    main()
