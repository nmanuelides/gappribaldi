#!/usr/bin/env python3
"""Fichas curadas en español rioplatense para los códigos más probables en un
C350 W204 (M272 + 7G-Tronic). Genera app/src/main/assets/dtc/curados_es.json.

Uso: python tools/curados.py

Cada ficha: t título, e explicación, c causas (de más a menos probable),
r qué revisar, g gravedad (verde/amarillo/rojo), m nota "frecuente en este motor".
Las notas "m" son orientativas: cuentan fallas conocidas del M272 / 722.9,
no son un diagnóstico seguro.
"""
import json, os

F = {}

def f(codigos, t, e, c, r, g="amarillo", m=None):
    for cod in codigos.split():
        d = {"t": t, "e": e, "c": c, "r": r, "g": g}
        if m: d["m"] = m
        F[cod] = d

def bancos(base1, base2, t, e, c, r, g="amarillo", m=None):
    """Misma ficha para banco 1 (lado derecho, cilindros 1-3) y banco 2 (izquierdo, 4-6)."""
    f(base1, t + " (banco 1, lado derecho)", e, c, r, g, m)
    f(base2, t + " (banco 2, lado izquierdo)", e, c, r, g, m)

M_BALANCE = ("Frecuente en este motor: en los M272 fabricados hasta 2008 aprox. (número de motor hasta "
             "2729..30 468993) el engranaje del eje de balanceo se desgasta antes de tiempo y desfasa la "
             "distribución. Es una reparación grande (hay que bajar el motor). No es un diagnóstico seguro: "
             "primero hay que descartar los imanes/variadores de levas y medir el desfase con escáner.")
M_ADMISION = ("Frecuente en este motor: el múltiple de admisión de plástico tiene unas palancas y varillas que "
              "mueven las mariposas internas y se rompen con los años. También se resecan las mangueras de "
              "ventilación del cárter. Suele dar mezcla pobre y códigos de las mariposas del múltiple.")
M_IMANES = ("Frecuente en este motor: los imanes (solenoides) de los variadores de levas pierden aceite por el "
            "conector; el aceite puede subir por el cable hasta la computadora o las sondas. Conviene mirar si "
            "hay aceite en las fichas.")
M_CAJA = ("Frecuente en esta caja: en la 7G-Tronic (722.9) fallan los sensores de velocidad que están en la "
          "placa electrónica dentro de la caja (placa conductora / cuerpo de válvulas). La caja suele quedar "
          "en emergencia, clavada en una marcha. No es seguro que sea eso: primero revisar nivel y estado del "
          "aceite y la ficha de la caja, que suele perder aceite.")

# ── Distribución / variadores ────────────────────────────────────────────────
f("P0016 P0017", "Cigüeñal y árbol de levas desfasados (banco 1, lado derecho)",
  "La computadora compara la posición del cigüeñal con la de los árboles de levas. Este código dice que en la "
  "bancada derecha no coinciden como deberían: la distribución está corrida o un variador no vuelve a su lugar.",
  ["Engranaje del eje de balanceo desgastado (cadena de distribución corrida)",
   "Variador de levas trabado o imán del variador sucio/defectuoso",
   "Cadena de distribución estirada",
   "Aceite muy viejo o bajo, que no deja trabajar a los variadores",
   "Sensor de posición de levas o su cableado"],
  ["No seguir andando muchos kilómetros así: si la cadena salta, el daño al motor es grave",
   "Revisar nivel y estado del aceite",
   "Pedir que midan con escáner el desfase real de los árboles de levas",
   "Revisar imanes de los variadores (fichas con aceite)"],
  "rojo", M_BALANCE)
f("P0018 P0019", "Cigüeñal y árbol de levas desfasados (banco 2, lado izquierdo)",
  "La computadora compara la posición del cigüeñal con la de los árboles de levas. En la bancada izquierda no "
  "coinciden como deberían: la distribución está corrida o un variador no vuelve a su lugar.",
  ["Variador de levas trabado o imán del variador sucio/defectuoso",
   "Cadena de distribución estirada o engranaje del eje de balanceo desgastado",
   "Aceite muy viejo o bajo",
   "Sensor de posición de levas o su cableado"],
  ["No seguir andando muchos kilómetros así", "Revisar nivel y estado del aceite",
   "Medir con escáner el desfase real de los árboles de levas", "Revisar imanes de los variadores"],
  "rojo", M_BALANCE)
bancos("P0010 P0013", "P0020 P0023", "Imán del variador de levas: falla eléctrica",
  "El imán (solenoide) que mueve el variador del árbol de levas tiene un problema eléctrico: está cortado, en "
  "corto o con la ficha en mal estado. P0010/P0020 son de admisión; P0013/P0023 de escape.",
  ["Imán del variador defectuoso", "Ficha o cable con aceite, sulfatado o cortado", "Falla en la computadora (raro)"],
  ["Mirar si la ficha del imán tiene aceite", "Medir la resistencia del imán", "Revisar el cable hasta la computadora"],
  "amarillo", M_IMANES)
bancos("P0011 P0012 P0014 P0015", "P0021 P0022 P0024 P0025", "Variador de levas fuera de posición",
  "El árbol de levas no llega al avance que la computadora le pide, o tarda demasiado. El motor puede perder "
  "fuerza, consumir más o regular mal.",
  ["Aceite viejo, bajo o de viscosidad incorrecta", "Imán del variador sucio o trabado",
   "Variador de levas gastado", "Distribución desfasada (cadena o engranaje de balanceo)"],
  ["Cambiar aceite y filtro si están vencidos (usar el especificado por Mercedes)",
   "Revisar y limpiar imanes de los variadores", "Si persiste, medir el desfase de la distribución"],
  "amarillo", M_IMANES)
f("P0340 P0341 P0342 P0343 P0345 P0346 P0347 P0348 P0365 P0366 P0367 P0368 P0390 P0391 P0392 P0393",
  "Sensor de posición del árbol de levas",
  "Uno de los cuatro sensores que leen la posición de los árboles de levas da una señal ausente o incoherente. "
  "El motor puede tardar en arrancar o andar a los tirones.",
  ["Sensor de levas defectuoso", "Ficha o cable dañado (a veces con aceite)", "Distribución desfasada"],
  ["Revisar ficha y cable del sensor", "Reemplazar el sensor (es accesible y barato)",
   "Si aparece junto con P0016 a P0019, mirar primero la distribución"])
f("P0335 P0336 P0337 P0338 P0339", "Sensor de posición del cigüeñal",
  "El sensor que le dice a la computadora cómo gira el motor falla o da una señal intermitente. Sin esa señal "
  "el motor se apaga o no arranca, sobre todo en caliente.",
  ["Sensor de cigüeñal defectuoso (falla típica en caliente)", "Ficha o cable dañado", "Corona fónica dañada (raro)"],
  ["Reemplazar el sensor de cigüeñal", "Revisar ficha y cableado"], "rojo",
  "Frecuente en Mercedes de esta época: el sensor de cigüeñal falla en caliente y el motor se para solo; "
  "al enfriarse vuelve a arrancar.")

# ── Mezcla / admisión ────────────────────────────────────────────────────────
bancos("P0171 P2187", "P0174 P2189", "Mezcla pobre",
  "El motor está recibiendo demasiado aire o poca nafta, y la computadora ya no puede compensarlo. "
  "P2187/P2189 indican que pasa sobre todo en ralentí.",
  ["Entrada de aire falsa: mangueras de ventilación del cárter, juntas o múltiple de admisión",
   "Caudalímetro (MAF) sucio o que mide de menos", "Baja presión de nafta (bomba o filtro)",
   "Inyectores sucios", "Pérdida en el escape antes de la sonda lambda"],
  ["Escuchar silbidos con el motor en marcha y revisar mangueras de goma resecas",
   "Prueba de humo para encontrar entradas de aire", "Limpiar o medir el caudalímetro",
   "Medir presión de nafta", "Mirar en \"Datos en vivo\" los ajustes de nafta: si los dos bancos están altos, "
   "el problema es común (aire o nafta)"],
  "amarillo", M_ADMISION)
bancos("P0172 P2188", "P0175 P2190", "Mezcla rica",
  "El motor está recibiendo nafta de más o aire de menos. Gasta más, puede oler a nafta y a la larga daña "
  "el catalizador.",
  ["Caudalímetro (MAF) defectuoso", "Inyector que gotea", "Regulador o presión de nafta alta",
   "Sonda lambda defectuosa", "Filtro de aire muy tapado", "Válvula de purga del cánister abierta"],
  ["Revisar filtro de aire", "Medir caudalímetro y presión de nafta", "Revisar si algún inyector pierde"])
f("P0170 P0173", "Ajuste de mezcla fuera de rango",
  "La corrección de nafta que hace la computadora llegó a su límite. Puede ser por mezcla pobre o rica.",
  ["Entrada de aire falsa", "Caudalímetro (MAF) defectuoso", "Presión de nafta incorrecta", "Sonda lambda defectuosa"],
  ["Mirar los ajustes de nafta en \"Datos en vivo\"", "Buscar entradas de aire", "Medir caudalímetro"],
  "amarillo", M_ADMISION)
f("P2004 P2005 P2006 P2007", "Mariposas del múltiple de admisión trabadas",
  "Dentro del múltiple de admisión hay unas mariposas que cambian el recorrido del aire. No se mueven o no "
  "llegan a su posición (P2004/P2005: quedan abiertas; P2006/P2007: quedan cerradas).",
  ["Palancas o varillas del múltiple rotas", "Diafragma de vacío del actuador pinchado",
   "Manguera de vacío suelta", "Válvula que conmuta el vacío defectuosa"],
  ["Mirar las palancas de plástico en el frente del múltiple (suelen verse rotas o sueltas)",
   "Revisar mangueras de vacío", "Existen kits de reparación de las palancas; si no, se cambia el múltiple"],
  "amarillo", M_ADMISION)
f("P2008 P2009 P2010 P2011 P2012 P2013 P2014 P2015 P2016 P2017 P2018 P2019 P2020 P2021 P2022",
  "Mariposas del múltiple de admisión: actuador o sensor",
  "Falla eléctrica o de lectura en el sistema que mueve las mariposas internas del múltiple de admisión.",
  ["Palancas del múltiple rotas, que dejan al sensor leyendo mal", "Sensor de posición de las mariposas",
   "Válvula de conmutación o su cableado"],
  ["Revisar el mecanismo del múltiple", "Revisar fichas y cables del sensor y la válvula"], "amarillo", M_ADMISION)
f("P0100 P0101 P0102 P0103 P0104", "Caudalímetro de aire (MAF)",
  "El sensor que mide cuánto aire entra al motor da valores que no cierran. El motor puede andar sin fuerza, "
  "regular mal o gastar de más.",
  ["Caudalímetro sucio o defectuoso", "Entrada de aire entre el caudalímetro y el motor",
   "Filtro de aire tapado o mal colocado", "Ficha o cable"],
  ["Revisar filtro de aire y que la caja del filtro cierre bien", "Limpiar el caudalímetro con limpiador específico",
   "Revisar mangueras de admisión"])
f("P0105 P0106 P0107 P0108", "Sensor de presión del múltiple (MAP)",
  "El sensor de presión de la admisión da valores fuera de lo esperado.",
  ["Sensor defectuoso", "Manguera o toma de vacío rota", "Ficha o cable"],
  ["Revisar el sensor y su ficha", "Buscar pérdidas de vacío"])
f("P0110 P0111 P0112 P0113 P0114", "Sensor de temperatura del aire de admisión",
  "La temperatura del aire que mide el motor no es creíble. En este auto el sensor viene dentro del caudalímetro.",
  ["Sensor (caudalímetro) defectuoso", "Ficha o cable"], ["Revisar ficha del caudalímetro"], "verde")
f("P0115 P0116 P0117 P0118 P0119", "Sensor de temperatura del refrigerante",
  "La computadora no confía en la temperatura del motor que le llega. Puede arrancar mal, gastar más o "
  "prender los electroventiladores sin motivo.",
  ["Sensor de temperatura defectuoso", "Ficha sulfatada o cable cortado", "Bajo nivel de refrigerante"],
  ["Revisar nivel de refrigerante en frío", "Revisar ficha del sensor", "Reemplazar el sensor"])
f("P0125 P0128", "El motor no llega a temperatura (termostato)",
  "El motor tarda demasiado en calentar o no llega a la temperatura normal. Casi siempre el termostato quedó abierto.",
  ["Termostato trabado abierto", "Sensor de temperatura que mide de menos", "Bajo nivel de refrigerante"],
  ["Mirar en \"Datos en vivo\" si la temperatura llega a 85-95 °C andando", "Cambiar el termostato"],
  "verde", "Frecuente en este motor: el termostato se queda abierto con los años. No es urgente, pero el motor "
  "frío gasta más nafta y se desgasta más.")
f("P0217", "Motor recalentado",
  "La computadora registró temperatura de motor excesiva.",
  ["Falta de refrigerante (pérdida)", "Termostato trabado cerrado", "Electroventilador que no funciona",
   "Bomba de agua", "Radiador tapado"],
  ["Parar el motor y dejar enfriar. No abrir el depósito en caliente", "Revisar nivel y pérdidas",
   "Verificar que arranque el electroventilador"], "rojo")

# ── Encendido ────────────────────────────────────────────────────────────────
f("P0300", "Fallas de encendido en varios cilindros",
  "El motor está fallando (\"ratea\") en más de un cilindro. La nafta sin quemar llega al catalizador y lo puede "
  "arruinar; si el testigo de motor titila, hay que parar.",
  ["Bujías gastadas", "Bobinas de encendido", "Entrada de aire falsa o mezcla pobre",
   "Baja presión de nafta", "Inyectores"],
  ["Si el testigo titila, no seguir manejando", "Revisar bujías y bobinas", "Ver si hay códigos de mezcla pobre",
   "Medir presión de nafta"], "rojo")
for n in range(1, 7):
    f(f"P030{n}", f"Falla de encendido en el cilindro {n}",
      f"El cilindro {n} no está quemando bien. Se siente como tirones o vibración. La nafta sin quemar puede "
      "arruinar el catalizador.",
      ["Bobina de encendido de ese cilindro", "Bujía gastada", "Inyector de ese cilindro",
       "Entrada de aire cerca de ese cilindro", "Baja compresión (raro)"],
      ["Si el testigo titila, no seguir manejando",
       "Cruzar la bobina con otro cilindro y ver si la falla se muda", "Revisar las bujías"],
      "rojo", f"Los cilindros 1 a 3 están en la bancada derecha (lado acompañante) y 4 a 6 en la izquierda.")
f("P0351 P0352 P0353 P0354 P0355 P0356 P2300 P2301 P2303 P2304 P2306 P2307 P2309 P2310 P2312 P2313 P2315 P2316",
  "Bobina de encendido: falla eléctrica",
  "El circuito de una bobina de encendido tiene un problema eléctrico. El último número de P035x indica el cilindro.",
  ["Bobina defectuosa", "Ficha o cable de la bobina", "Bujía en mal estado que sobrecarga la bobina"],
  ["Revisar ficha de la bobina", "Cruzar bobinas entre cilindros para confirmar", "Cambiar bujías si están vencidas"])
f("P0325 P0326 P0327 P0328 P0330 P0331 P0332 P0333", "Sensor de detonación (pistoneo)",
  "El sensor que escucha si el motor pistonea da una señal anormal. La computadora atrasa el encendido por "
  "seguridad y el motor pierde algo de fuerza.",
  ["Sensor de detonación defectuoso", "Ficha o cable", "Nafta de bajo octanaje"],
  ["Cargar nafta de 98 octanos", "Revisar ficha y cable del sensor"], "verde")

# ── Sondas lambda / catalizadores ────────────────────────────────────────────
bancos("P0130 P0131 P0132 P0133 P0134 P2195 P2196 P2A00", "P0150 P0151 P0152 P0153 P0154 P2197 P2198 P2A03",
  "Sonda lambda anterior al catalizador",
  "La sonda que mide los gases antes del catalizador responde lento, quedó fija o da valores fuera de rango. "
  "Con esa señal la computadora regula la nafta.",
  ["Sonda lambda agotada", "Pérdida de escape antes de la sonda", "Entrada de aire o mezcla muy desviada",
   "Ficha o cable (a veces con aceite)"],
  ["Ver si hay códigos de mezcla pobre/rica: resolver esos primero", "Revisar ficha de la sonda", "Reemplazar la sonda"])
bancos("P0136 P0137 P0138 P0139 P0140 P2270 P2271", "P0156 P0157 P0158 P0159 P0160 P2272 P2273",
  "Sonda lambda posterior al catalizador",
  "La sonda que controla al catalizador da una señal anormal. No afecta mucho cómo anda el motor.",
  ["Sonda lambda agotada", "Pérdida de escape", "Ficha o cable"],
  ["Revisar ficha y cable", "Reemplazar la sonda"], "verde")
bancos("P0030 P0031 P0032 P0036 P0037 P0038 P0135 P0141", "P0050 P0051 P0052 P0056 P0057 P0058 P0155 P0161",
  "Calefacción de la sonda lambda",
  "Cada sonda tiene una resistencia que la calienta para que funcione enseguida. Esa resistencia o su circuito falló.",
  ["Sonda lambda con la resistencia cortada", "Fusible de las sondas", "Ficha o cable"],
  ["Revisar fusible", "Medir la resistencia de calefacción de la sonda", "Reemplazar la sonda"], "verde")
bancos("P0420 P0421 P0422", "P0430 P0431 P0432", "Catalizador con bajo rendimiento",
  "La sonda de atrás del catalizador ve casi lo mismo que la de adelante: el catalizador ya no limpia bien los gases.",
  ["Catalizador agotado", "Sonda lambda posterior defectuosa", "Pérdida de escape",
   "Fallas de encendido o mezcla rica previas que lo dañaron"],
  ["Descartar pérdidas de escape y fallas de encendido", "Comparar las sondas en \"Datos en vivo\"",
   "No es urgente para el motor, pero contamina más"], "verde")

# ── Aire secundario / evaporativo ────────────────────────────────────────────
f("P0410 P0411 P0412 P0413 P0414 P0418 P0419 P0491 P0492 P2440 P2441 P2442 P2443 P2444 P2445",
  "Sistema de aire secundario",
  "En frío, una bomba eléctrica sopla aire al escape para calentar rápido los catalizadores. Ese sistema no "
  "está funcionando bien. No afecta el andar.",
  ["Bomba de aire secundario defectuosa", "Relé o fusible de la bomba", "Válvula de corte trabada",
   "Manguera de vacío rota"],
  ["Escuchar si la bomba suena unos segundos al arrancar en frío", "Revisar relé y fusible", "Revisar mangueras de vacío"],
  "verde", "Frecuente en este motor: la bomba de aire secundario y su relé fallan con los años.")
f("P0440 P0441 P0442 P0443 P0444 P0445 P0446 P0447 P0448 P0449 P0450 P0451 P0452 P0453 P0455 P0456 P0457 P0496 P0497",
  "Sistema de vapores de nafta (evaporativo)",
  "El sistema que atrapa los vapores del tanque tiene una pérdida o una válvula que no trabaja. No afecta el andar.",
  ["Tapa de nafta mal cerrada o con el burlete gastado", "Válvula de purga del cánister",
   "Manguera del sistema suelta o rajada", "Válvula de cierre del cánister"],
  ["Ajustar bien la tapa de nafta (o cambiarla) y esperar unos días", "Revisar mangueras", "Probar la válvula de purga"],
  "verde")
f("P0460 P0461 P0462 P0463 P0464", "Sensor de nivel de nafta",
  "El medidor de nafta da una lectura incoherente. La aguja puede marcar mal.",
  ["Flotante del tanque defectuoso", "Ficha o cable"], ["Controlar el consumo a mano hasta repararlo"], "verde")

# ── Combustible / inyección ──────────────────────────────────────────────────
f("P0087 P0190 P0191 P0192 P0193", "Presión de nafta",
  "La presión de nafta es baja o el sensor que la mide no da una señal creíble.",
  ["Bomba de nafta débil", "Filtro de nafta tapado", "Regulador de presión", "Sensor de presión o su cable"],
  ["Medir la presión de nafta con manómetro", "Cambiar el filtro si está vencido"])
f("P0201 P0202 P0203 P0204 P0205 P0206 P0261 P0262 P0264 P0265 P0267 P0268 P0270 P0271 P0273 P0274 P0276 P0277",
  "Inyector: falla eléctrica",
  "El circuito de un inyector está abierto o en corto. El último número de P020x indica el cilindro.",
  ["Inyector defectuoso", "Ficha o cable del inyector"],
  ["Revisar ficha del inyector", "Medir resistencia del inyector"], "amarillo")
f("P0230 P0231 P0232 P0627 P0628 P0629", "Circuito de la bomba de nafta",
  "Falla eléctrica en el mando de la bomba de nafta. El motor puede no arrancar o pararse.",
  ["Relé o módulo de mando de la bomba", "Fusible", "Bomba de nafta", "Cableado"],
  ["Revisar fusible y relé", "Escuchar si la bomba zumba al dar contacto"], "rojo")

# ── Mariposa / acelerador ────────────────────────────────────────────────────
f("P0120 P0121 P0122 P0123 P0220 P0221 P0222 P0223 P0638 P2100 P2101 P2102 P2103 P2110 P2111 P2112 P2118 P2119 P2135",
  "Mariposa electrónica",
  "El cuerpo de mariposa (que regula el aire según el acelerador) no llega a la posición pedida o sus sensores "
  "no coinciden. El auto suele quedar sin fuerza, en modo de emergencia.",
  ["Cuerpo de mariposa sucio o gastado", "Ficha o cable", "Falla del motor eléctrico de la mariposa"],
  ["Limpiar el cuerpo de mariposa", "Revisar ficha", "Si se cambia, hay que hacer la adaptación con escáner"],
  "amarillo")
f("P2122 P2123 P2127 P2128 P2138", "Pedal del acelerador",
  "Los dos sensores del pedal del acelerador no coinciden. El auto puede quedar sin respuesta al acelerar.",
  ["Pedal del acelerador defectuoso", "Ficha o cable"], ["Revisar ficha del pedal", "Reemplazar el pedal"], "amarillo")
f("P0500 P0501 P0502 P0503", "Señal de velocidad del auto",
  "La computadora del motor no recibe bien la velocidad del auto (viene del ABS/ESP).",
  ["Sensor de rueda del ABS", "Falla en el módulo ESP", "Problema de comunicación CAN"],
  ["Ver si el módulo de frenos/ESP tiene códigos", "Revisar sensores de rueda"])
f("P0505 P0506 P0507", "Ralentí fuera de rango",
  "El motor regula más alto o más bajo de lo que la computadora intenta mantener.",
  ["Entrada de aire falsa", "Cuerpo de mariposa sucio", "Ventilación del cárter"],
  ["Buscar entradas de aire", "Limpiar el cuerpo de mariposa"], "verde", M_ADMISION)

# ── Alimentación eléctrica / computadora ─────────────────────────────────────
f("P0560 P0561 P0562 P0563 U3003", "Tensión de batería fuera de rango",
  "La computadora vio una tensión demasiado baja o alta. Con batería floja aparecen fallas falsas en muchos módulos.",
  ["Batería agotada o vieja", "Alternador o regulador de tensión", "Bornes flojos o sulfatados", "Masa en mal estado"],
  ["Medir batería en reposo (debe dar unos 12,4-12,7 V) y con motor en marcha (13,5-14,7 V)",
   "Limpiar y ajustar bornes", "Si hay muchos códigos raros juntos, sospechar primero de la batería"])
f("P0600 P0601 P0602 P0603 P0604 P0605 P0606 P0607 P060A P060B P060C P060D P061A P061B",
  "Falla interna de la computadora del motor",
  "La computadora del motor detectó un problema en su propia memoria o procesador.",
  ["Baja tensión o picos durante un arranque", "Aceite que entró a la computadora por el cableado",
   "Computadora defectuosa"],
  ["Revisar batería y masas", "Mirar si la ficha de la computadora tiene aceite", "Borrar y ver si vuelve"],
  "amarillo", M_IMANES)
f("P0520 P0521 P0522 P0523", "Sensor de presión / nivel de aceite",
  "La señal del sensor de aceite no es creíble.",
  ["Sensor de aceite defectuoso", "Nivel de aceite bajo", "Ficha o cable"],
  ["Medir el nivel de aceite ya mismo", "Si el nivel está bien, revisar el sensor"], "amarillo")
f("P0480 P0481 P0483 P0484 P0485", "Electroventilador",
  "El mando del electroventilador del radiador tiene una falla. El motor puede recalentar en el tránsito.",
  ["Electroventilador o su módulo de control", "Fusible", "Cableado"],
  ["Vigilar la temperatura", "Verificar que el ventilador arranque con el aire acondicionado encendido"], "amarillo")

# ── Caja automática 7G-Tronic ────────────────────────────────────────────────
f("P0715 P0716 P0717 P0718 P2765 P2766 P2767 P2768", "Sensor de velocidad de turbina de la caja",
  "La caja no recibe bien la señal de uno de sus sensores internos de velocidad. Suele quedar en modo de "
  "emergencia, sin pasar cambios.",
  ["Sensores de velocidad de la placa electrónica interna de la caja", "Ficha de la caja con pérdida de aceite",
   "Aceite de caja bajo o muy degradado"],
  ["No forzar el auto si quedó en una sola marcha", "Revisar la ficha de la caja y el nivel de aceite",
   "Llevar a un especialista en cajas Mercedes"], "rojo", M_CAJA)
f("P0720 P0721 P0722 P0723", "Sensor de velocidad de salida de la caja",
  "La caja no recibe bien la señal de velocidad de salida. Puede quedar en emergencia o pasar los cambios mal.",
  ["Sensor de velocidad en la placa electrónica de la caja", "Ficha de la caja con aceite", "Cableado"],
  ["Revisar ficha de la caja y nivel de aceite", "Llevar a un especialista en cajas"], "rojo", M_CAJA)
f("P0700", "La caja automática registró una falla",
  "Es un aviso de la computadora del motor: la caja tiene códigos propios guardados. Hay que leer el módulo de la caja.",
  ["Cualquier falla registrada por la caja"], ["Mirar los códigos del módulo \"Caja automática\" en esta app"])
f("P0705 P0706 P0707 P0708 P0709", "Sensor de posición de la palanca de cambios",
  "La caja no sabe con seguridad en qué posición está la palanca (P, R, N, D).",
  ["Módulo de la palanca selectora", "Líquido derramado sobre la palanca (típico)", "Cableado"],
  ["Revisar si se volcó alguna bebida en la consola", "Leer códigos del módulo de la palanca"], "amarillo")
f("P0730 P0731 P0732 P0733 P0734 P0735 P0729 P076F", "Relación de marcha incorrecta",
  "En alguna marcha la caja patina: las vueltas de entrada y salida no dan la relación que corresponde.",
  ["Aceite de caja bajo o quemado", "Embragues internos gastados", "Cuerpo de válvulas / placa electrónica"],
  ["Revisar nivel y estado del aceite de caja", "No exigir el auto", "Diagnóstico con especialista"], "rojo", M_CAJA)
f("P0740 P0741 P0742 P0743 P0744 P2757 P2758 P2759 P2760 P2761 P2762 P2763 P2764", "Embrague del convertidor de par",
  "El embrague que bloquea el convertidor patina o no responde. Se siente como vibración a velocidad constante "
  "(entre 60 y 100 km/h).",
  ["Aceite de caja degradado", "Convertidor de par gastado", "Electroválvula del convertidor"],
  ["Cambiar aceite y filtro de caja con el aceite correcto", "Si sigue, evaluar el convertidor"], "amarillo",
  "Frecuente en esta caja: vibración por el embrague del convertidor; muchas veces mejora con cambio de aceite "
  "y actualización de software en un taller especializado.")
f("P0710 P0711 P0712 P0713 P0714", "Temperatura del aceite de la caja",
  "El sensor de temperatura del aceite de caja da un valor que no es creíble.",
  ["Sensor en la placa electrónica de la caja", "Ficha de la caja con aceite"],
  ["Revisar ficha de la caja"], "amarillo", M_CAJA)
f("P0750 P0751 P0752 P0753 P0755 P0756 P0757 P0758 P0760 P0761 P0762 P0763 P0765 P0766 P0767 P0768 P0770 P0771 P0772 P0773 "
  "P0745 P0746 P0747 P0748 P0775 P0776 P0777 P0778 P0795 P0796 P0797 P0798",
  "Electroválvula de la caja",
  "Una de las electroválvulas que manejan los cambios o la presión dentro de la caja no responde bien.",
  ["Cuerpo de válvulas / placa electrónica de la caja", "Aceite sucio", "Ficha de la caja"],
  ["Revisar aceite y ficha de la caja", "Diagnóstico con especialista en cajas"], "rojo", M_CAJA)

# ── Comunicación entre módulos ───────────────────────────────────────────────
f("U0001 U0002 U0073 U0074", "Falla en la red CAN",
  "Los módulos del auto no se pudieron comunicar entre sí por un momento.",
  ["Batería baja durante el arranque", "Ficha o cable de la red dañado", "Un módulo defectuoso que tira la red"],
  ["Revisar la batería", "Ver qué módulos no responden en el escaneo", "Si pasó una sola vez, borrar y observar"])
f("U0100", "Sin comunicación con la computadora del motor",
  "Otro módulo dejó de recibir mensajes de la computadora del motor.",
  ["Batería baja", "Alimentación o masa de la computadora del motor", "Cableado CAN"],
  ["Revisar batería y fusibles", "Si el auto anda normal y pasó una vez, borrar y observar"])
f("U0101", "Sin comunicación con la computadora de la caja",
  "Otro módulo dejó de recibir mensajes de la caja automática.",
  ["Batería baja", "Ficha de la caja con aceite", "Placa electrónica de la caja"],
  ["Revisar batería", "Revisar la ficha de la caja"], "amarillo", M_CAJA)
f("U0121 U0122 U0415 U0416", "Sin comunicación / datos inválidos del ABS-ESP",
  "Un módulo no recibe datos correctos del sistema de frenos ABS/ESP.",
  ["Batería baja", "Módulo ESP", "Sensor de rueda", "Cableado CAN"],
  ["Leer los códigos del módulo de frenos/ESP", "Revisar batería"])
f("U0140 U0141 U0142 U0146 U0151 U0155 U0164 U0168 U0184",
  "Sin comunicación con otro módulo del auto",
  "Un módulo dejó de recibir mensajes de otro (carrocería, tablero, airbag, climatizador, llave o radio, según el código).",
  ["Batería baja o caída de tensión al arrancar", "El módulo que falta está sin alimentación o defectuoso",
   "Cableado o ficha de la red CAN"],
  ["Revisar la batería primero: es la causa más común", "Ver si el módulo que falta responde en el escaneo",
   "Si pasó una sola vez, borrar y observar"], "verde")

out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dtc", "curados_es.json")
with open(out, "w", encoding="utf-8") as fh:
    json.dump(F, fh, ensure_ascii=False, indent=0, sort_keys=True)
print(len(F), "fichas curadas")
