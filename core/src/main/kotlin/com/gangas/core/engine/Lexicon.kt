package com.gangas.core.engine

/** Diccionarios (es/pt) usados para filtrar falsos positivos típicos de Mercado Libre. */
object Lexicon {
    /** Palabras que indican accesorio o repuesto en vez del producto buscado. */
    val ACCESSORY = listOf(
        "funda", "fundas", "case", "carcasa", "estuche", "vidrio", "templado", "mica", "film",
        "protector", "lamina", "skin", "sticker", "calco", "cargador", "cable", "adaptador",
        "repuesto", "repuestos", "pantalla", "display", "modulo", "flex", "bateria", "tapa",
        "caja vacia", "solo caja", "manual", "soporte", "correa", "malla",
        "control remoto", "mando", "joystick", "capinha", "pelicula", "capa", "carregador",
        "peca", "pecas", "molde", "plantilla", "maqueta", "replica de exhibicion",
        "dummy", "juguete", "llavero", "poster", "cuadro",
    )

    /** Precios que no son el precio real (señuelo). */
    val BAIT = listOf(
        "consultar", "consulte", "consultar precio", "a convenir", "leer descripcion",
        "ver descripcion", "lea la descripcion", "sena", "senia", "seña", "reserva", "reservar",
        "anticipo", "adelanto", "cuota", "cuotas", "alquiler", "alquilo", "por dia", "por hora",
        "por mes", "precio por", "valor por", "permuto", "permuta", "canje", "sinal", "aluguel",
        "a combinar", "orcamento", "presupuesto", "cotizar", "precio x mayor", "x mayor",
        "por mayor", "mayorista", "precio de referencia", "no comprar", "no ofertar",
        "publicacion informativa", "servicio tecnico", "reparacion", "arreglo",
    )

    /** Señales de riesgo en el título. */
    val RISKY = listOf(
        "replica", "replika", "aaa", "clon", "clone", "simil", "tipo original", "generico",
        "roto", "rota", "no funciona", "para reparar", "para repuesto", "bloqueado", "icloud",
        "reportado", "sin imei", "averiado", "defectuoso", "quebrado", "trincado", "com defeito",
        "no enciende", "piezas", "partes", "falla", "detalle", "1a linea", "primera linea",
    )

    /** Patrones "para <producto>" muy característicos de accesorios ("funda para iphone"). */
    val ACCESSORY_PREFIXES = listOf("para", "compatible", "compativel", "p/", "for")
}
