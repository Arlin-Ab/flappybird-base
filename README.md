# Flappy Bird OpenGL (LWJGL / Java)

Proyecto del primer parcial. Mini-juego estilo Flappy Bird hecho con **Java + LWJGL + OpenGL 3.3 core profile**, en perspectiva 2D usando coordenadas NDC.

## Integrantes

- Arlin Manuel

## Características

- **Pájaro compuesto por figuras geométricas:** cuerpo (círculo), pico (triángulo), ala animada (triángulo oscilante), cola (triángulo) y ojo (círculo blanco + pupila negra). El pájaro se inclina arriba/abajo según su velocidad vertical.
- **Modo 2 jugadores** simultáneos en la misma ventana. Cada pájaro tiene su propio color, puntaje y estado vivo/muerto. La partida termina sólo cuando **ambos** mueren.
- **Dificultad progresiva:** cada 5 puntos del jugador con mayor puntaje sube un nivel; la velocidad y la frecuencia de tuberías aumentan dentro de límites que mantienen el juego jugable. El nivel actual se muestra en el HUD y en el título de la ventana.
- **Interfaz mejorada:** cielo con degradado vertical, sol con halo, nubes con parallax, suelo con franja de pasto, panel de marcador con dígitos tipo "display de 7 segmentos" por jugador, indicador de vivo/muerto y pantallas de inicio / game over con paneles superpuestos.

## Controles

| Acción | Jugador 1 | Jugador 2 |
| --- | --- | --- |
| Saltar / Iniciar | `ESPACIO` | `W` o `Flecha Arriba` |
| Reiniciar (en Game Over) | `R`, `ENTER` o `ESPACIO` | mismas |
| Salir | `ESC` | `ESC` |

## Requisitos

- Java 17+
- Maven 3.9+
- LWJGL 3.3.3 (descargado automáticamente por Maven)
- Sistema operativo Windows (el `pom.xml` actual trae `natives-windows`)

## Compilar y ejecutar

Desde la carpeta del proyecto (donde está `pom.xml`):

```bash
mvn clean compile exec:exec -DmainClass=com.graphics.AppFlappyBird
```

O, en dos pasos:

```bash
mvn clean compile
mvn exec:exec -DmainClass=com.graphics.AppFlappyBird
```

## Estructura del código

El juego está separado en clases por responsabilidad. Todo el código está comentado en español.

```
src/main/java/com/graphics/
├── AppFlappyBird.java              # Entry point: crea ventana, inicializa GLFW/OpenGL, corre el loop
└── flappy/
    ├── Game.java                   # Estado global, lista de pájaros/tuberías, dificultad, pantallas
    ├── Bird.java                   # Pájaro: física, animación, render compuesto
    ├── Pipe.java                   # Tubería: render, movimiento, colisión AABB
    ├── Renderer.java               # Shaders, VAOs/VBOs y primitivas (rect / círculo / triángulo)
    ├── InputManager.java           # Lectura de teclado y detección de flancos
    └── Hud.java                    # Marcador, paneles y dígitos de 7 segmentos
```

### Notas técnicas

- Un único **programa de shaders** con uniforms para `uOffset` (traslación), `uScale` (escala), `uRot` (rotación) y `uColor` / `uColor2` / `uUseGradient` (color sólido o degradado vertical).
- Tres **mallas reutilizables** subidas una sola vez al inicio:
  - Quad unitario (rectángulos, fondo, tuberías, suelo).
  - Triangle fan unitario (círculos: cuerpo, ojo, sol, nubes).
  - Triángulo unitario (pico, ala, cola).
- Las **partes del pájaro** se dibujan rotando manualmente sus offsets locales por la rotación global del pájaro antes de pasar la traslación al shader. Eso permite que la composición rote como un solo cuerpo.
- La **dificultad** se recalcula cada frame en función del puntaje más alto entre los dos jugadores. La velocidad de las tuberías se interpola entre 0.62 y 1.40 y el intervalo de spawn entre 1.5s y 0.9s.

## Cambios respecto a la versión base

- Refactor de un solo archivo a 6 clases con responsabilidades separadas.
- Shader extendido con rotación y degradados.
- Pájaro compuesto reemplazando el rectángulo amarillo.
- Soporte real de 2 jugadores con física y puntaje independiente.
- Sistema de dificultad por niveles y HUD con dígitos sin depender de fuentes.
- Fondo con degradado, nubes con parallax, sol, suelo con pasto y pantallas de inicio / game over.
