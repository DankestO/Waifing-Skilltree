<img src="src/main/resources/logo.png" alt="Logo de Waifing Skilltree" width="128">

# Waifing Skilltree

*English: [README.md](README.md)*

Un framework de árboles de habilidades por datapack para Minecraft Forge 1.20.1, inspirado en Passive Skill Trees y Path of Exile.

Waifing Skilltree añade un árbol de habilidades a pantalla completa en el que los jugadores gastan puntos de habilidad en nodos que dan atributos, desbloquean recetas, ejecutan comandos y más. El mod no incluye ningún árbol: cada árbol se define en un datapack, así que quien crea el modpack o el servidor decide exactamente qué pueden aprender sus jugadores.

## Cómo funciona

- Los jugadores abren el árbol con una tecla (**U** por defecto) y gastan puntos en nodos conectados.
- Los puntos se compran con niveles de experiencia. Cada compra cuesta un nivel más que la anterior. En la config se pueden poner puntos gratis por nivel, puntos iniciales y un límite de compras.
- Los nodos pueden tener varios niveles, y sus bonus crecen con cada nivel.
- Los árboles se cargan desde datapacks y se recargan con `/reload`.

## Recompensas de los nodos

- Atributos de vanilla o de cualquier otro mod, siempre activos o solo con una condición.
- Recetas añadidas al libro de recetas, o recetas que solo puede fabricar quien tiene el nodo.
- Recetas de clic derecho: sostén un objeto en el cursor y haz clic derecho sobre otro en el inventario.
- Comandos que se ejecutan al ganar o devolver un nivel.
- Comandos bloqueados hasta que el jugador tiene un nodo concreto.

## Condiciones

Un bonus de atributo puede depender de lo que hace el jugador, por ejemplo:

- el objeto en la mano principal o secundaria (objetos sueltos o tags);
- las piezas de armadura que lleva;
- vida, hambre, nivel de luz, altura, hora del día, clima, bioma o dimensión;
- agacharse, correr, estar en el agua o en llamas;
- efectos beneficiosos o perjudiciales activos;
- armas o armaduras encantadas (los modificadores de Tinkers' Construct cuentan como encantamientos);
- categorías de armas de Better Combat, armas a dos manos y dos armas;
- ranuras de Curios.

Las condiciones que dependen de otro mod simplemente no se activan si ese mod no está instalado.

## Herramientas de diseño

- **Requisitos:** logros, valores de atributos, objetos o tener otros nodos.
- **Elecciones:** desde un nodo solo se puede seguir uno de varios caminos. Otros nodos pueden dar más elecciones, compartidas entre grupos de elecciones, o empezar bloqueadas en cero.
- **Grupos limitados:** solo se pueden coger cierto número de nodos de un grupo, por ejemplo dos keystones de cuatro.
- **Límites de nivel:** los niveles altos de un nodo pueden pedir otros nodos antes.
- **Árboles exclusivos** para sistemas de clases, con nodos que desbloquean otros árboles.
- **Conexiones:** de un solo sentido, curvas y de larga distancia.

## Para el jugador

- Zoom y desplazamiento suaves, búsqueda por nombre de nodo o por atributo, y caminos resaltados.
- Tooltips detallados con bonus, condiciones, requisitos y el motivo por el que un nodo está bloqueado.
- Objeto de reinicio opcional y comandos de administración (`/skilltree respec`, `/skilltree points`).
- Modo hardcore opcional: al morir se reinicia todo el árbol.

## Editor de árboles

Los árboles se crean con un editor web gratuito que funciona en el navegador, sin instalar nada. Permite colocar y conectar nodos de forma visual, editar atributos y condiciones, ver el árbol como lo vería un jugador, comprobar errores y exportar el datapack directamente.

Editor: [enlace al editor]

## Datapacks

Este mod no incluye ningún árbol. Instala un datapack que traiga uno, o crea el tuyo con el editor.

Árbol oficial: [enlace al datapack de Waifing Skilltree]

## Compatibilidad

Integraciones opcionales, ninguna obligatoria:

- **Apothic Attributes:** muestra el árbol como origen de sus bonus en la pantalla de atributos.
- **Better Combat:** condiciones por categoría de arma.
- **Curios API:** condiciones por ranura de curio.
- **Tinkers' Construct:** los modificadores de las herramientas cuentan como encantamientos.
- Se pueden usar atributos de cualquier mod en los árboles.

## Formato del datapack

```
data/<espacio>/waifingskilltree/trees/<nombre>.json
data/<espacio>/waifingskilltree/click_recipes/<nombre>.json
data/<espacio>/recipes/<nombre>.json
```

Todos los campos, condiciones y opciones de configuración están descritos en [docs/datapack-format.es.md](docs/datapack-format.es.md).

## Compilar

Necesita JDK 17.

1. Descarga `ApothicAttributes-1.20.1-1.3.7.jar` de CurseForge y ponlo en `libs/` (solo para compilar, no va dentro del mod).
2. Ejecuta `./gradlew build`. El mod se genera en `build/libs/`.

`./gradlew runClient` abre un cliente de prueba.

## Licencia

Copyright (C) 2026 Dankest0. Publicado bajo la [GNU General Public License v3.0](LICENSE) o posterior.
