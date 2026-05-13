package com.graphics;

import java.util.Random;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

/**
 * Game:
 * Lógica principal del juego Flappy Bird para dos jugadores.
 * Maneja los estados del juego (Inicio, Jugando, Game Over),
 * la inicialización de GLFW/OpenGL, el bucle principal y la gestión
 * de dos jugadores independientes con sus propios controles y puntuación.
 *
 * Arquitectura:
 *  - Game usa Renderer para dibujar.
 *  - Game crea y gestiona dos instancias de Bird.
 *  - Game usa PipeManager para obstáculos y colisiones.
 *
 * Controles:
 *  - Jugador 1: ESPACIO para saltar.
 *  - Jugador 2: W para saltar.
 *  - ESC: salir del juego.
 *  - R (en Game Over): reiniciar partida.
 *
 * La partida termina solo cuando AMBOS jugadores han chocado.
 */
public class Game {

    // ─── Constantes de ventana ──────────────────────────────────────────────
    /** Ancho de la ventana en píxeles. */
    private static final int ANCHO = 900;
    /** Alto de la ventana en píxeles. */
    private static final int ALTO = 700;

    // ─── Constantes de física ───────────────────────────────────────────────
    /** Aceleración de gravedad (negativa = hacia abajo). */
    private static final float GRAVEDAD = -1.9f;
    /** Impulso vertical al saltar. */
    private static final float IMPULSO_SALTO = 0.85f;
    /** Velocidad máxima de caída para mantener control. */
    private static final float VELOCIDAD_MAX_CAIDA = -1.8f;
    /** Límite inferior de colisión (coincide con la parte superior del pasto visible). */
    private static final float LIMITE_SUELO = -0.78f;

    // ─── Posiciones horizontales de los jugadores ───────────────────────────
    /** Posición X del jugador 1 en NDC (más a la izquierda). */
    private static final float BIRD1_X = -0.35f;
    /** Posición X del jugador 2 en NDC (más a la derecha). */
    private static final float BIRD2_X =  -0.15f;

    // ─── Colores de los jugadores ───────────────────────────────────────────
    /** Color del cuerpo del Jugador 1 (amarillo dorado). */
    private static final float[] COLOR_CUERPO1 = {0.98f, 0.84f, 0.20f};
    /** Color del ala del Jugador 1 (naranja). */
    private static final float[] COLOR_ALA1    = {0.95f, 0.60f, 0.10f};
    /** Color del pico del Jugador 1 (naranja oscuro). */
    private static final float[] COLOR_PICO1   = {1.00f, 0.50f, 0.00f};
    /** Color de la cola del Jugador 1. */
    private static final float[] COLOR_COLA1   = {0.90f, 0.55f, 0.10f};

    /** Color del cuerpo del Jugador 2 (rojo). */
    private static final float[] COLOR_CUERPO2 = {0.90f, 0.30f, 0.24f};
    /** Color del ala del Jugador 2 (rojo oscuro). */
    private static final float[] COLOR_ALA2    = {0.70f, 0.15f, 0.10f};
    /** Color del pico del Jugador 2 (naranja rojizo). */
    private static final float[] COLOR_PICO2   = {1.00f, 0.35f, 0.10f};
    /** Color de la cola del Jugador 2. */
    private static final float[] COLOR_COLA2   = {0.75f, 0.20f, 0.12f};

    // ─── Colores de fondo y decoración ──────────────────────────────────────
    /** Color del cielo (azul claro). */
    private static final float[] COLOR_CIELO = {0.53f, 0.81f, 0.92f};
    /** Color del suelo (marrón). */
    private static final float[] COLOR_SUELO = {0.55f, 0.27f, 0.07f};
    /** Color del pasto (verde). */
    private static final float[] COLOR_PASTO = {0.20f, 0.75f, 0.30f};
    /** Color de las nubes (blanco). */
    private static final float[] COLOR_NUBE = {0.95f, 0.95f, 0.98f};

    // ─── Estados del juego ──────────────────────────────────────────────────
    /** El juego está en pantalla de inicio. */
    private static final int ESTADO_INICIO = 0;
    /** El juego está en curso. */
    private static final int ESTADO_JUGANDO = 1;
    /** El juego terminó (ambos jugadores murieron). */
    private static final int ESTADO_GAME_OVER = 2;

    // ─── Componentes del juego ──────────────────────────────────────────────
    /** Ventana GLFW. */
    private long window;
    /** Renderer (shaders + dibujo de primitivas). */
    private Renderer renderer;
    /** Jugador 1 (control: ESPACIO). */
    private Bird jugador1;
    /** Jugador 2 (control: W). */
    private Bird jugador2;
    /** Gestor de tuberías (obstáculos). */
    private PipeManager pipeManager;

    // ─── Estado de la partida ───────────────────────────────────────────────
    /** Estado actual de la máquina de estados. */
    private int estado;

    // ─── Control de input (detección de flanco) ─────────────────────────────
    /** Estado previo de la tecla ESPACIO (para detectar flanco de subida). */
    private boolean prevSpace;
    /** Estado previo de la tecla W. */
    private boolean prevW;
    /** Estado previo de la tecla R (reinicio). */
    private boolean prevR;

    // ─── Nubes decorativas ──────────────────────────────────────────────────
    /** Posiciones pregeneradas de las nubes del fondo. */
    private final float[][] nubes;

    // ─── Semillas aleatorias para nubes ─────────────────────────────────────
    private final Random randomNubes;

    // ═══════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Crea una nueva instancia del juego.
     * Inicializa los componentes pero no arranca GLFW todavía.
     */
    public Game() {
        this.renderer = new Renderer();
        this.pipeManager = new PipeManager();
        this.estado = ESTADO_INICIO;
        this.randomNubes = new Random(42); // semilla fija para nubes consistentes

        // Crear jugadores con sus colores distintivos.
        this.jugador1 = new Bird(BIRD1_X, COLOR_CUERPO1, COLOR_ALA1,
                                  COLOR_PICO1, COLOR_COLA1);
        this.jugador2 = new Bird(BIRD2_X, COLOR_CUERPO2, COLOR_ALA2,
                                  COLOR_PICO2, COLOR_COLA2);

        // Generar posiciones de nubes decorativas (5 nubes).
        this.nubes = new float[5][2];
        for (int i = 0; i < 5; i++) {
            nubes[i][0] = -0.8f + randomNubes.nextFloat() * 1.6f; // X entre -0.8 y 0.8
            nubes[i][1] =  0.3f + randomNubes.nextFloat() * 0.6f; // Y entre 0.3 y 0.9
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FLUJO PRINCIPAL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Punto de entrada: inicializa GLFW/OpenGL, ejecuta el bucle principal
     * y libera recursos al salir.
     */
    public void run() {
        inicializarGLFW();
        buclePrincipal();
        limpiar();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INICIALIZACIÓN DE GLFW Y OPENGL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Inicializa GLFW, crea la ventana, activa el contexto OpenGL
     * y configura el Renderer con los shaders y geometrías base.
     */
    private void inicializarGLFW() {
        // Iniciar GLFW (biblioteca de ventanas y contexto).
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("No se pudo iniciar GLFW");
        }

        // Configurar "hints" (opciones) para la creación de la ventana.
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        // Solicitar OpenGL 3.3 Core Profile.
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        // Crear la ventana.
        window = GLFW.glfwCreateWindow(ANCHO, ALTO, "Flappy Bird - Dos Jugadores", 0, 0);
        if (window == 0) {
            throw new RuntimeException("No se pudo crear la ventana GLFW");
        }

        // Hacer que el contexto OpenGL de esta ventana sea el activo.
        GLFW.glfwMakeContextCurrent(window);
        // Activar VSync: sincronizar refresco con el monitor (evita tearing).
        GLFW.glfwSwapInterval(1);
        // Mostrar la ventana.
        GLFW.glfwShowWindow(window);

        // Cargar las funciones de OpenGL para la versión solicitada.
        GL.createCapabilities();

        // Inicializar el Renderer (shaders, VBOs, VAOs).
        renderer.inicializar();

        // Configurar título inicial.
        actualizarTitulo();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BUCLE PRINCIPAL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Bucle principal del juego.
     * En cada iteración:
     *  1. Calcula el delta time (dt) para movimiento independiente de FPS.
     *  2. Procesa el input del teclado.
     *  3. Actualiza la lógica según el estado actual.
     *  4. Renderiza la escena.
     *  5. Intercambia buffers y procesa eventos.
     */
    private void buclePrincipal() {
        float ultimoTiempo = (float) GLFW.glfwGetTime();

        while (!GLFW.glfwWindowShouldClose(window)) {
            // Calcular delta time (tiempo transcurrido desde el frame anterior).
            float tiempoActual = (float) GLFW.glfwGetTime();
            float dt = tiempoActual - ultimoTiempo;
            ultimoTiempo = tiempoActual;

            // Limitar dt para evitar saltos bruscos si la app se congela.
            if (dt > 0.033f) {
                dt = 0.033f;
            }

            // Pipeline del frame.
            procesarInput();
            actualizar(dt);
            renderizar();

            // Doble buffer: mostrar el frame dibujado y procesar eventos.
            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PROCESAMIENTO DE INPUT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lee el estado del teclado y actúa según el estado del juego.
     * Usa detección de flanco (transición de no-presionado a presionado)
     * para que cada pulsación dispare una sola acción.
     */
    private void procesarInput() {
        // ESC: salir del juego en cualquier momento.
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            GLFW.glfwSetWindowShouldClose(window, true);
            return;
        }

        // Leer estado actual de teclas relevantes.
        boolean spaceAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean wAhora     = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean rAhora     = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

        switch (estado) {
            case ESTADO_INICIO:
                // En pantalla de inicio, cualquier tecla de salto inicia el juego.
                // Se aplica impulso inicial a ambos pájaros para que no caigan inmediatamente.
                if ((spaceAhora && !prevSpace) || (wAhora && !prevW)) {
                    estado = ESTADO_JUGANDO;
                    jugador1.saltar(IMPULSO_SALTO);
                    jugador2.saltar(IMPULSO_SALTO);
                }
                break;

            case ESTADO_JUGANDO:
                // Jugador 1: ESPACIO para saltar.
                if (spaceAhora && !prevSpace) {
                    jugador1.saltar(IMPULSO_SALTO);
                }
                // Jugador 2: W para saltar.
                if (wAhora && !prevW) {
                    jugador2.saltar(IMPULSO_SALTO);
                }
                break;

            case ESTADO_GAME_OVER:
                // ESPACIO, W o R para reiniciar la partida.
                if ((spaceAhora && !prevSpace) || (wAhora && !prevW)
                        || (rAhora && !prevR)) {
                    reiniciarJuego();
                    estado = ESTADO_JUGANDO;
                }
                break;
        }

        // Actualizar estados previos para la siguiente iteración.
        prevSpace = spaceAhora;
        prevW = wAhora;
        prevR = rAhora;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACTUALIZACIÓN DE LÓGICA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Actualiza la lógica del juego según el estado actual.
     *
     * @param dt Delta time en segundos.
     */
    private void actualizar(float dt) {
        if (estado != ESTADO_JUGANDO) {
            return;
        }

        // Actualizar física de cada jugador.
        // Los pájaros vivos reciben física completa + verificación de límites.
        // Los pájaros muertos solo caen por gravedad (ya están fuera de juego).
        if (jugador1.isVivo()) {
            jugador1.actualizar(dt, GRAVEDAD);
            // Limitar velocidad de caída del jugador 1.
            if (jugador1.getVelY() < VELOCIDAD_MAX_CAIDA) {
                jugador1.setVelY(VELOCIDAD_MAX_CAIDA);
            }
            // Verificar colisión con límites de pantalla (techo/suelo).
            verificarLimitesPantalla(jugador1);
        } else {
            // Pájaro muerto: solo aplicamos gravedad para que caiga.
            jugador1.actualizar(dt, GRAVEDAD);
            if (jugador1.getVelY() < VELOCIDAD_MAX_CAIDA) {
                jugador1.setVelY(VELOCIDAD_MAX_CAIDA);
            }
        }

        if (jugador2.isVivo()) {
            jugador2.actualizar(dt, GRAVEDAD);
            // Limitar velocidad de caída del jugador 2.
            if (jugador2.getVelY() < VELOCIDAD_MAX_CAIDA) {
                jugador2.setVelY(VELOCIDAD_MAX_CAIDA);
            }
            // Verificar colisión con límites de pantalla (techo/suelo).
            verificarLimitesPantalla(jugador2);
        } else {
            // Pájaro muerto: solo aplicamos gravedad para que caiga.
            jugador2.actualizar(dt, GRAVEDAD);
            if (jugador2.getVelY() < VELOCIDAD_MAX_CAIDA) {
                jugador2.setVelY(VELOCIDAD_MAX_CAIDA);
            }
        }

        // Actualizar tuberías (movimiento, colisiones, puntuación).
        pipeManager.actualizar(dt, jugador1, jugador2);

        // Actualizar dificultad según puntuación combinada.
        int puntajeTotal = jugador1.getPuntaje() + jugador2.getPuntaje();
        pipeManager.actualizarDificultad(puntajeTotal);

        // Actualizar título de ventana con información en tiempo real.
        actualizarTitulo();

        // Verificar condición de Game Over: ambos jugadores muertos.
        if (!jugador1.isVivo() && !jugador2.isVivo()) {
            estado = ESTADO_GAME_OVER;
            actualizarTitulo();
        }
    }

    /**
     * Verifica si un pájaro ha chocado con el techo o el suelo de la pantalla.
     * Si es así, lo marca como muerto.
     *
     * @param bird Pájaro a verificar.
     */
    private void verificarLimitesPantalla(Bird bird) {
        float[] aabb = bird.getAABB();
        float birdTop    = aabb[3];
        float birdBottom = aabb[2];

        if (birdTop >= 1.0f || birdBottom <= LIMITE_SUELO) {
            bird.morir();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RENDERIZADO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Renderiza la escena completa según el estado actual.
     * Orden de dibujo (de atrás hacia adelante):
     *  1. Fondo (cielo, nubes, suelo).
     *  2. Tuberías.
     *  3. Pájaros.
     *  4. Overlays de UI (pantalla de inicio, game over, HUD).
     */
    private void renderizar() {
        // Limpiar el framebuffer con el color del cielo.
        renderer.limpiarPantalla(COLOR_CIELO[0], COLOR_CIELO[1], COLOR_CIELO[2]);

        // Activar el programa de shaders una sola vez por frame.
        renderer.usarShader();

        // Dibujar elementos del fondo.
        dibujarFondo();

        // Dibujar tuberías (obstáculos).
        pipeManager.dibujar(renderer);

        // Dibujar pájaros (siempre visibles; los muertos caen y desaparecen de pantalla).
        jugador1.dibujar(renderer);
        jugador2.dibujar(renderer);

        // Dibujar overlays de UI según el estado.
        switch (estado) {
            case ESTADO_INICIO:
                dibujarPantallaInicio();
                break;
            case ESTADO_GAME_OVER:
                dibujarPantallaGameOver();
                break;
            case ESTADO_JUGANDO:
                dibujarHUD();
                break;
        }
    }

    /**
     * Dibuja el fondo decorativo: suelo, pasto y nubes.
     */
    private void dibujarFondo() {
        // ── Suelo (franja marrón en la parte inferior) ──
        renderer.dibujarRectangulo(0.0f, -0.92f, 2.0f, 0.16f,
                COLOR_SUELO[0], COLOR_SUELO[1], COLOR_SUELO[2], 0.0f);

        // ── Pasto (franja verde sobre el suelo) ──
        renderer.dibujarRectangulo(0.0f, -0.83f, 2.0f, 0.04f,
                COLOR_PASTO[0], COLOR_PASTO[1], COLOR_PASTO[2], 0.0f);

        // ── Nubes decorativas ──
        // Cada nube se compone de 3 elipses blancas superpuestas.
        for (float[] nube : nubes) {
            float nx = nube[0];
            float ny = nube[1];
            // Nube: 3 círculos/elipses en formación horizontal.
            renderer.dibujarElipse(nx, ny, 0.12f, 0.06f,
                    COLOR_NUBE[0], COLOR_NUBE[1], COLOR_NUBE[2]);
            renderer.dibujarElipse(nx - 0.08f, ny + 0.02f, 0.07f, 0.05f,
                    COLOR_NUBE[0], COLOR_NUBE[1], COLOR_NUBE[2]);
            renderer.dibujarElipse(nx + 0.08f, ny + 0.02f, 0.07f, 0.05f,
                    COLOR_NUBE[0], COLOR_NUBE[1], COLOR_NUBE[2]);
        }
    }

    /**
     * Dibuja la pantalla de inicio con indicaciones para los jugadores.
     * Muestra un panel central con el título y las instrucciones de controles.
     */
    private void dibujarPantallaInicio() {
        // ── Panel de fondo semitransparente (oscuro) ──
        renderer.dibujarRectangulo(0.0f, 0.05f, 1.2f, 0.55f,
                0.10f, 0.12f, 0.20f, 0.0f);

        // ── Título del juego (barra amarilla decorativa) ──
        renderer.dibujarRectangulo(0.0f, 0.25f, 0.90f, 0.06f,
                0.98f, 0.84f, 0.20f, 0.0f);

        // ── Subtítulo: "DOS JUGADORES" (barra roja) ──
        renderer.dibujarRectangulo(0.0f, 0.15f, 0.55f, 0.05f,
                0.90f, 0.30f, 0.24f, 0.0f);

        // ── Instrucciones Jugador 1 (amarillo) ──
        // Indicador de color del jugador 1.
        renderer.dibujarElipse(-0.25f, 0.02f, 0.03f, 0.03f,
                0.98f, 0.84f, 0.20f);
        // Barra de texto simulada para "JUGADOR 1: ESPACIO".
        renderer.dibujarRectangulo(-0.05f, 0.04f, 0.50f, 0.03f,
                0.98f, 0.84f, 0.20f, 0.0f);

        // ── Instrucciones Jugador 2 (rojo) ──
        // Indicador de color del jugador 2.
        renderer.dibujarElipse(-0.25f, -0.06f, 0.03f, 0.03f,
                0.90f, 0.30f, 0.24f);
        // Barra de texto simulada para "JUGADOR 2: W".
        renderer.dibujarRectangulo(-0.05f, -0.04f, 0.45f, 0.03f,
                0.90f, 0.30f, 0.24f, 0.0f);

        // ── Mensaje "Presiona ESPACIO o W para empezar" (barra blanca) ──
        renderer.dibujarRectangulo(0.0f, -0.18f, 0.70f, 0.03f,
                0.90f, 0.90f, 0.90f, 0.0f);
    }

    /**
     * Dibuja la pantalla de Game Over mostrando puntajes finales y ganador.
     */
    private void dibujarPantallaGameOver() {
        // ── Panel de fondo oscuro ──
        renderer.dibujarRectangulo(0.0f, 0.05f, 1.2f, 0.55f,
                0.10f, 0.12f, 0.20f, 0.0f);

        // ── Título "GAME OVER" (barra roja) ──
        renderer.dibujarRectangulo(0.0f, 0.25f, 0.60f, 0.06f,
                0.90f, 0.20f, 0.15f, 0.0f);

        // ── Puntaje Jugador 1 (amarillo) ──
        renderer.dibujarElipse(-0.28f, 0.10f, 0.03f, 0.03f,
                0.98f, 0.84f, 0.20f);
        // Barra que representa visualmente el puntaje (más larga = más puntos).
        float barraAncho1 = Math.min(0.60f, 0.05f + jugador1.getPuntaje() * 0.03f);
        renderer.dibujarRectangulo(0.08f, 0.11f, barraAncho1, 0.025f,
                0.98f, 0.84f, 0.20f, 0.0f);

        // ── Puntaje Jugador 2 (rojo) ──
        renderer.dibujarElipse(-0.28f, 0.0f, 0.03f, 0.03f,
                0.90f, 0.30f, 0.24f);
        float barraAncho2 = Math.min(0.60f, 0.05f + jugador2.getPuntaje() * 0.03f);
        renderer.dibujarRectangulo(0.08f, 0.01f, barraAncho2, 0.025f,
                0.90f, 0.30f, 0.24f, 0.0f);

        // ── Indicador de ganador ──
        String resultado = determinarGanador();
        float[] colorGanador;
        if (resultado.equals("J1")) {
            colorGanador = new float[]{0.98f, 0.84f, 0.20f};
        } else if (resultado.equals("J2")) {
            colorGanador = new float[]{0.90f, 0.30f, 0.24f};
        } else {
            colorGanador = new float[]{0.80f, 0.80f, 0.80f}; // empate: gris
        }
        // Barra que indica el ganador.
        renderer.dibujarRectangulo(0.0f, -0.10f, 0.70f, 0.03f,
                colorGanador[0], colorGanador[1], colorGanador[2], 0.0f);

        // ── Mensaje de reinicio ──
        renderer.dibujarRectangulo(0.0f, -0.20f, 0.75f, 0.03f,
                0.85f, 0.85f, 0.85f, 0.0f);
    }

    /**
     * Dibuja el HUD (Head-Up Display) durante el juego.
     * Muestra indicadores de puntuación de cada jugador como barras
     * en la parte superior de la pantalla.
     */
    private void dibujarHUD() {
        // ── Indicador Jugador 1 (esquina superior izquierda) ──
        // Pequeño círculo del color del jugador.
        renderer.dibujarElipse(-0.85f, 0.92f, 0.025f, 0.025f,
                0.98f, 0.84f, 0.20f);
        // Barra de puntuación (longitud proporcional al puntaje).
        float barra1 = Math.min(0.30f, jugador1.getPuntaje() * 0.02f + 0.02f);
        renderer.dibujarRectangulo(-0.80f + barra1 * 0.5f, 0.92f, barra1, 0.02f,
                0.98f, 0.84f, 0.20f, 0.0f);

        // ── Indicador Jugador 2 (esquina superior derecha) ──
        renderer.dibujarElipse(0.85f, 0.92f, 0.025f, 0.025f,
                0.90f, 0.30f, 0.24f);
        float barra2 = Math.min(0.30f, jugador2.getPuntaje() * 0.02f + 0.02f);
        renderer.dibujarRectangulo(0.80f - barra2 * 0.5f, 0.92f, barra2, 0.02f,
                0.90f, 0.30f, 0.24f, 0.0f);

        // ── Indicador de nivel de dificultad (centro superior) ──
        int nivel = pipeManager.getNivelDificultad();
        float nivBarra = Math.min(0.20f, nivel * 0.03f + 0.02f);
        renderer.dibujarRectangulo(0.0f, 0.93f, nivBarra, 0.015f,
                1.0f, 1.0f, 1.0f, 0.0f);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Actualiza el título de la ventana con información del estado del juego,
     * puntuaciones y nivel de dificultad.
     */
    private void actualizarTitulo() {
        int p1 = jugador1.getPuntaje();
        int p2 = jugador2.getPuntaje();
        int nivel = pipeManager.getNivelDificultad();
        String titulo = "Flappy Bird 2P | P1: " + p1 + " | P2: " + p2
                      + " | Nivel: " + nivel;

        switch (estado) {
            case ESTADO_INICIO:
                titulo += " | ESPACIO o W para empezar";
                break;
            case ESTADO_JUGANDO:
                String vivos = "";
                if (jugador1.isVivo() && jugador2.isVivo()) {
                    vivos = "Ambos vivos";
                } else if (jugador1.isVivo()) {
                    vivos = "J1 vivo";
                } else if (jugador2.isVivo()) {
                    vivos = "J2 vivo";
                }
                titulo += " | " + vivos;
                break;
            case ESTADO_GAME_OVER:
                String resultado = determinarGanador();
                if (resultado.equals("EMPATE")) {
                    titulo += " | GAME OVER - EMPATE";
                } else {
                    titulo += " | GAME OVER - Ganador: " + resultado;
                }
                titulo += " | Presiona ESPACIO/W/R para reiniciar";
                break;
        }

        GLFW.glfwSetWindowTitle(window, titulo);
    }

    /**
     * Determina el ganador comparando las puntuaciones.
     *
     * @return "J1" si ganó el jugador 1, "J2" si ganó el jugador 2,
     *         "EMPATE" si tienen la misma puntuación.
     */
    private String determinarGanador() {
        int p1 = jugador1.getPuntaje();
        int p2 = jugador2.getPuntaje();
        if (p1 > p2) return "J1";
        if (p2 > p1) return "J2";
        return "EMPATE";
    }

    /**
     * Reinicia la partida: resetea ambos pájaros y el gestor de tuberías.
     */
    private void reiniciarJuego() {
        jugador1.reiniciar();
        jugador2.reiniciar();
        pipeManager.reiniciar();
        actualizarTitulo();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIMPIEZA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Libera todos los recursos: Renderer (VAOs, VBOs, shaders),
     * ventana GLFW y termina GLFW.
     */
    private void limpiar() {
        renderer.liberar();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUNTO DE ENTRADA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Método main: crea una instancia del juego y la ejecuta.
     *
     * @param args Argumentos de línea de comandos (no se usan).
     */
    public static void main(String[] args) {
        new Game().run();
    }
}
