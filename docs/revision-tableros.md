# Revisión de tableros contra su imagen de referencia

Proceso armado a partir de las correcciones del tablero **Híbrido azul** (el primero que se pulió a mano).
Cada punto sale de algo que se marcó como distinto a la referencia. Se aplica a cada tablero, uno por uno,
antes de darlo por terminado.

Herramienta: `tools/revision_tablero.py` (recortes ampliados y muestreo de colores de la referencia, en las
mismas coordenadas que usa el código: la imagen escalada a 2000 px de ancho).

## 0. Medir, no estimar

- Ampliar la referencia por zonas (cada reloj, cada barra, cada texto chico) antes de dibujar. Mirar la imagen
  entera esconde los detalles que después se notan.
- Sacar centros, radios y ángulos de la referencia y anotarlos en el código. Un error típico fue suponer el
  centro: se corrige comparando el radio arriba y abajo del mismo aro.
- Los colores y degradés se **muestrean** de la referencia (perfiles a lo largo del radio, en varios ángulos),
  no se inventan.

## 1. Geometría

- Una barra o banda mantiene **el mismo grosor y alto en todo su recorrido**, incluidas sus partes finales
  (zona roja, último tramo).
- Los arcos son **círculos perfectos** (semicírculos, anillos). Una foto en perspectiva no se copia como
  elipse.
- **Muchas referencias son fotos sacadas de costado o desde abajo.** Se reconstruye la vista de frente: si hay dos relojes,
  son **del mismo tamaño** (uno más chico en la foto es solo perspectiva), los centros son concéntricos (un
  centro "corrido" es el ángulo de la foto) y los elementos a los costados son simétricos.
- **Las partes físicas del auto no son interfaz**: el volante, las levas, sus luces, el marco del tablero o
  reflejos. Si en la foto tapan algo, se dibuja lo que hay detrás.
- El brillo de una foto engaña: reflejos y luz ambiente aclaran el fondo. Si el fondo del tablero se ve gris o
  marrón por el reflejo, en realidad es **casi negro**.
- Las agujas y marcadores salen **del centro exacto** del arco y apuntan a las rayas.
- La línea de base de una barra inferior sigue la forma de la referencia (por ejemplo, baja en el centro y
  sube hacia los costados, dejando lugar a la información de abajo).

## 2. Estructura: cuántas partes y dónde

- Contar las **secciones / divisiones** de la referencia y respetar la cantidad (no inventar 26 segmentos
  donde hay 5).
- Ubicar las divisiones **donde caen en la referencia**: casi siempre coinciden con los números.
- Contar los **aros**: cuántos hay, cuál es opaco y cuál transparente, en qué aro van los números, la unidad
  y las marcas.
- Las **marcas** de un aro: hacia dónde apuntan (adentro / afuera), si tienen sombra, y si hay una marca
  correspondiente en las secciones.

## 3. Volumen (efecto 3D)

- Una banda con volumen tiene **caras** (la de adelante, angosta y clara; la que va hacia el fondo, ancha y
  más oscura). El quiebre entre caras se marca por **salto de brillo**, nunca con una línea divisoria.
- La iluminación suele venir de un costado: el degradé de la cara de atrás **cambia con el ángulo** (de un
  lado oscuro→claro, del otro claro→oscuro). Se dibuja en tajadas finas, cada una con su degradé.
- Las divisiones pueden **seguir hacia adentro** desvaneciéndose.

## 4. Degradés y colores

- Copiar el tono exacto (en general no es gris neutro: es azul grisáceo, blanco azulado, etc.).
- En barras de progreso: cada sección encendida con **su propio degradé leve**; las apagadas como **un solo
  degradé continuo** de punta a punta, con cortes entre secciones.
- Arcos de color (acentos): núcleo claro con borde más saturado, como en la referencia.

## 5. Tipografía

- Comparar **grosor, ancho, alto y simetría** de la letra (por ejemplo: la referencia es más gruesa, más
  ancha y redonda que la que se usó). Cambiar de familia si hace falta, y de peso para los números.
- Mantener las proporciones entre textos (número grande, unidad, etiquetas).
- Los textos que siguen un arco en la referencia van **curvos** sobre ese arco.

## 6. Superposiciones

- Ningún texto pisa otro elemento (aros, bandas, barras, otros textos).
- Los cortes de un aro para dejar lugar a un texto se calculan con el **ancho real** del texto, con margen.
- Revisar en especial: unidades sobre aros, números en la parte de abajo de un reloj (cerca de barras),
  textos de dos tamaños en el mismo renglón.

## 7. Datos

- Velocidad en **km/h** por defecto, con la unidad elegida en Ajustes (km/h o mph) en todo el tablero.
- Lo que la referencia muestra y el auto no tiene (funciones de auto eléctrico, música, navegación) se
  reemplaza por datos reales del OBD (vueltas, tensión, viaje, temperaturas), manteniendo el lugar y el
  estilo del original.

## Cómo aplicarlo

1. Recortar la referencia por zonas con `tools/revision_tablero.py recorte` y mirarlas ampliadas.
2. Medir centros y ángulos (`polar` en el recorte) y muestrear colores con `tools/revision_tablero.py perfil`.
3. Recorrer las secciones 1 a 7 y anotar cada diferencia con el código del tablero.
4. Corregir, compilar e instalar; pedir una captura para comparar lado a lado.
