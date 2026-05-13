package com.graphics;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * Game:
 * Clase principal que gestiona la lógica del juego, estados y dos jugadores.
 *
 * Estados del juego:
 * - INICIO: pantalla de bienvenida esperando que el jugador presione una tecla.
 * - JUEGO: partida activa con física, tuberías y colisiones.
 * - GAME_OVER: muestra ganador/puntajes y espera reinicio.
 *
 * Dos jugadores:
 * - Jugador 1 (P1): controla con ESPACIO. Color amarillo.
 * - Jugador 2 (P2): controla con Flecha Arriba. Color cyan.
 *
 * La partida termina cuando AMBOS pájaros han chocado.
 */
public class Game {
    private enum Estado { INICIO, ESPERANDO_JUGADORES, JUEGO, GAME_OVER }

    private final Renderer renderer;
    private final PipeManager pipeManager;
    private final Bird bird1;
    private final Bird bird2;

    private Estado estado;
    private boolean prevSpace;
    private boolean prevUp;
    private boolean prevR;
    private boolean p1Listo;
    private boolean p2Listo;
    private boolean countdownActivo;
    private int countdownValor;
    private float countdownTimer;

    private int ultimoPuntajeTotal;

    private int puntaje1;
    private int puntaje2;

    private static final int ANCHO = 900;
    private static final int ALTO = 700;
    private long window;

    public Game() {
        this.renderer = new Renderer();
        this.pipeManager = new PipeManager();
        this.bird1 = new Bird(-0.45f, 0.0f, new float[]{0.98f, 0.85f, 0.20f});
        this.bird2 = new Bird(-0.45f, 0.0f, new float[]{0.20f, 0.85f, 0.92f});
        this.estado = Estado.INICIO;
        this.puntaje1 = 0;
        this.puntaje2 = 0;
        this.prevSpace = false;
        this.prevUp = false;
        this.prevR = false;
        this.p1Listo = false;
        this.p2Listo = false;
        this.countdownActivo = false;
        this.countdownValor = 5;
        this.countdownTimer = 0f;
        this.ultimoPuntajeTotal = 0;
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        // Inicialización de GLFW y OpenGL.
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("No se pudo iniciar GLFW");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(ANCHO, ALTO, "Flappy Bird - 2 Jugadores", 0, 0);
        if (window == 0) {
            throw new RuntimeException("No se pudo crear la ventana");
        }

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);

        org.lwjgl.opengl.GL.createCapabilities();

        // Inicializar el renderer con shaders y geometry.
        renderer.init();

        // Estado inicial: listo para jugar.
        resetGame();
    }

    /**
     * Reinicia el juego al estado inicial.
     */
    private void resetGame() {
        bird1.reset();
        bird2.reset();
        pipeManager.reset();
        puntaje1 = 0;
        puntaje2 = 0;
        p1Listo = false;
        p2Listo = false;
        countdownActivo = false;
        countdownValor = 5;
        countdownTimer = 0f;
        estado = Estado.INICIO;
        actualizarTitulo();
    }

    /**
     * Procesa input del teclado:
     * - ESC: cerrar ventana.
     * - ESPACIO: jugador 1 se prepara / salta / reinicia.
     * - Flecha Arriba: jugador 2 se prepara / salta.
     * - R: reiniciar (en game over).
     */
    private void procesarInput() {
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            GLFW.glfwSetWindowShouldClose(window, true);
        }

        boolean spaceAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean upAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
        boolean rAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

        if (spaceAhora && !prevSpace) {
            if (estado == Estado.INICIO || estado == Estado.ESPERANDO_JUGADORES) {
                p1Listo = true;
                if (estado == Estado.INICIO) {
                    estado = Estado.ESPERANDO_JUGADORES;
                }
            } else if (estado == Estado.JUEGO) {
                if (bird1.isVivo()) {
                    bird1.saltar();
                }
            } else if (estado == Estado.GAME_OVER) {
                resetGame();
            }
        }
        prevSpace = spaceAhora;

        if (upAhora && !prevUp) {
            if (estado == Estado.INICIO || estado == Estado.ESPERANDO_JUGADORES) {
                p2Listo = true;
                if (estado == Estado.INICIO) {
                    estado = Estado.ESPERANDO_JUGADORES;
                }
            } else if (estado == Estado.JUEGO) {
                if (bird2.isVivo()) {
                    bird2.saltar();
                }
            }
        }
        prevUp = upAhora;

        // Reiniciar con R en game over.
        if (rAhora && !prevR && estado == Estado.GAME_OVER) {
            resetGame();
        }
        prevR = rAhora;
    }

    /**
     * Actualiza la lógica del juego (física, colisiones, puntaje).
     * @param dt Delta time en segundos.
     */
    private void actualizar(float dt) {
        if (estado == Estado.ESPERANDO_JUGADORES) {
            if (p1Listo && p2Listo && !countdownActivo) {
                countdownActivo = true;
                countdownValor = 5;
                countdownTimer = 0f;
            }
            if (countdownActivo) {
                countdownTimer += dt;
                if (countdownTimer >= 1.0f) {
                    countdownTimer = 0f;
                    countdownValor--;
                    if (countdownValor <= 0) {
                        estado = Estado.JUEGO;
                        bird1.saltar();
                        bird2.saltar();
                        pipeManager.activar();
                        countdownActivo = false;
                    }
                }
            }
            return;
        }
        if (estado != Estado.JUEGO) {
            return;
        }
        if (bird1.isVivo()) {
            bird1.actualizar(dt);
        }
        if (bird2.isVivo()) {
            bird2.actualizar(dt);
        }

        // Actualizar tuberías (movimiento y spawn).
        pipeManager.actualizar(dt);

        // Detectar colisiones con tuberías.
        if (pipeManager.colisionaConBird(bird1)) {
            bird1.setVivo(false);
        }
        if (pipeManager.colisionaConBird(bird2)) {
            bird2.setVivo(false);
        }

        // Revisar puntuación de cada jugador.
        puntaje1 += pipeManager.revisarPuntuacion(bird1);
        puntaje2 += pipeManager.revisarPuntuacion(bird2);

        // Aumentar dificultad solo cuando el puntaje total aumenta.
        int puntajeTotal = puntaje1 + puntaje2;
        if (puntajeTotal > ultimoPuntajeTotal) {
            pipeManager.aumentarDificultad(puntajeTotal);
        }
        ultimoPuntajeTotal = puntajeTotal;

        // Verificar fin del juego: ambos pájaros muertos.
        if (!bird1.isVivo() && !bird2.isVivo()) {
            estado = Estado.GAME_OVER;
        }

        actualizarTitulo();
    }

    /**
     * Renderiza el frame completo.
     */
    private void render() {
        // Fondo con cielo y nubes.
        renderer.dibujarFondo();

        // Dibujar todas las tuberías.
        for (PipeManager.Tuberia t : pipeManager.getTuberias()) {
            float gapTop = t.gapCentroY + (pipeManager.getGapAlto() * 0.5f);
            float gapBottom = t.gapCentroY - (pipeManager.getGapAlto() * 0.5f);

            float altoSuperior = 1.0f - gapTop;
            if (altoSuperior > 0.0f) {
                float yCentroSup = gapTop + (altoSuperior * 0.5f);
                renderer.dibujarTuberia(t.x, yCentroSup, pipeManager.getTuberiaAncho(), altoSuperior);
            }

            float altoInferior = gapBottom + 1.0f;
            if (altoInferior > 0.0f) {
                float yCentroInf = -1.0f + (altoInferior * 0.5f);
                renderer.dibujarTuberia(t.x, yCentroInf, pipeManager.getTuberiaAncho(), altoInferior);
            }
        }

        // Dibujar pájaros (si están vivos).
        if (bird1.isVivo()) {
            bird1.dibujar(renderer);
        }
        if (bird2.isVivo()) {
            bird2.dibujar(renderer);
        }

        // HUD: mostrar puntajes en pantalla.
        dibujarHUD();

        // Pantalla de inicio.
        if (estado == Estado.INICIO || estado == Estado.ESPERANDO_JUGADORES) {
            dibujarPantallaInicio();
        }

        // Countdown.
        if (countdownActivo) {
            dibujarCountdown();
        }

        // Pantalla de Game Over.
        if (estado == Estado.GAME_OVER) {
            dibujarPantallaGameOver();
        }
    }

    /**
     * Dibuja el HUD con los puntajes de ambos jugadores.
     */
private void dibujarHUD() {
        float[] colorP1 = p1Listo ? new float[]{0.98f, 0.85f, 0.20f} : new float[]{0.5f, 0.5f, 0.5f};
        float[] colorP2 = p2Listo ? new float[]{0.20f, 0.85f, 0.92f} : new float[]{0.5f, 0.5f, 0.5f};

        renderer.dibujarRect(-0.95f, 0.88f, 0.18f, 0.08f, colorP1[0], colorP1[1], colorP1[2], 0f);
        renderer.dibujarRect(0.77f, 0.88f, 0.18f, 0.08f, colorP2[0], colorP2[1], colorP2[2], 0f);

        dibujarP(-0.90f, 0.85f, colorP1);
        dibujar1(-0.80f, 0.85f, colorP1);

        dibujarP(0.82f, 0.85f, colorP2);
        dibujar2(0.92f, 0.85f, colorP2);
    }

    private void dibujarP(float cx, float cy, float[] color) {
        float bx = 0.005f;
        float sx = 0.003f;
        float sy = 0.018f;
        renderer.dibujarRect(cx, cy + sy * 0.5f, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx, cy + sy * 0.5f, bx * 2, sy * 0.5f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx + bx, cy + sy * 0.5f, sx, sy * 0.6f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx + bx, cy + sy * 0.5f, bx * 2, sy * 0.35f, color[0], color[1], color[2], 0f);
    }

    private void dibujarPantallaInicio() {
        renderer.dibujarRect(0f, 0.0f, 0.8f, 0.20f, 0.1f, 0.1f, 0.1f, 0f);
        renderer.dibujarRect(0f, 0.15f, 0.5f, 0.08f, 1f, 1f, 1f, 0f);

        renderer.dibujarRect(-0.35f, 0.02f, 0.15f, 0.04f, 0.98f, 0.85f, 0.20f, 0f);
        renderer.dibujarRect(-0.17f, 0.02f, 0.15f, 0.04f, 0.5f, 0.5f, 0.5f, 0f);

        renderer.dibujarRect(0.05f, 0.02f, 0.15f, 0.04f, 0.20f, 0.85f, 0.92f, 0f);
        renderer.dibujarRect(0.23f, 0.02f, 0.15f, 0.04f, 0.5f, 0.5f, 0.5f, 0f);

        renderer.dibujarRect(0f, -0.05f, 0.45f, 0.06f, 0.8f, 0.8f, 0.8f, 0f);
        renderer.dibujarRect(0f, -0.14f, 0.50f, 0.04f, 0.6f, 0.6f, 0.6f, 0f);
    }

    private void dibujarCountdown() {
        renderer.dibujarRect(-0.18f, -0.15f, 0.36f, 0.30f, 0.1f, 0.1f, 0.1f, 0f);

        float[] colorNum;
        if (countdownValor == 1) {
            colorNum = new float[]{0.2f, 0.9f, 0.2f};
        } else {
            colorNum = new float[]{1f, 1f, 1f};
        }

        if (countdownValor == 5) {
            dibujar5(0f, 0f, colorNum);
        } else if (countdownValor == 4) {
            dibujar4(0f, 0f, colorNum);
        } else if (countdownValor == 3) {
            dibujar3(0f, 0f, colorNum);
        } else if (countdownValor == 2) {
            dibujar2(0f, 0f, colorNum);
        } else if (countdownValor == 1) {
            dibujar1(0f, 0f, colorNum);
        }
    }

    private void dibujar1(float cx, float cy, float[] color) {
        float sx = 0.006f;
        float sy = 0.030f;
        renderer.dibujarRect(cx - sx, cy + sy * 0.8f, sx * 2f, sy * 0.4f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx, cy - sy * 0.5f, sx, sy * 1.5f, color[0], color[1], color[2], 0f);
    }

private void dibujar5(float cx, float cy, float[] color) {
        float bx = 0.012f;
        float by = 0.006f;
        float sx = 0.005f;
        float sy = 0.025f;
        renderer.dibujarRect(cx - bx, cy + by, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx - sx, cy + by, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx, cy - by - sy, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx + bx, cy - by - sy, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx, cy + by, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
    }

    private void dibujar2(float cx, float cy, float[] color) {
        float bx = 0.012f;
        float by = 0.006f;
        float sx = 0.005f;
        float sy = 0.025f;
        renderer.dibujarRect(cx - bx, cy + by, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx + bx, cy + by, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx, cy - by - sy, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx - sx, cy - by - sy, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx, cy - by - sy, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
    }

    private void dibujar4(float cx, float cy, float[] color) {
        float bx = 0.012f;
        float by = 0.006f;
        float sx = 0.005f;
        float sy = 0.025f;
        renderer.dibujarRect(cx - bx - sx, cy + by, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx, cy + by, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx + bx, cy + by, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx + bx, cy - by - sy, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx - sx, cy - by - sy, sx, sy, color[0], color[1], color[2], 0f);
    }

    private void dibujar3(float cx, float cy, float[] color) {
        float bx = 0.012f;
        float by = 0.006f;
        float sx = 0.005f;
        float sy = 0.025f;
        renderer.dibujarRect(cx + bx, cy + by, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx, cy + by, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx + bx, cy - by - sy, sx, sy, color[0], color[1], color[2], 0f);
        renderer.dibujarRect(cx - bx, cy - by - sy, bx * 2, sy * 0.6f, color[0], color[1], color[2], 0f);
    }

    /**
     * Dibuja la pantalla de Game Over con ganador y puntajes.
     */
    private void dibujarPantallaGameOver() {
        renderer.dibujarRect(0f, 0.2f, 0.9f, 0.35f, 0.15f, 0.15f, 0.2f, 0f);
        renderer.dibujarRect(0f, 0.38f, 0.45f, 0.08f, 1f, 0.2f, 0.2f, 0f);

        if (puntaje1 > puntaje2) {
            renderer.dibujarRect(-0.25f, 0.20f, 0.50f, 0.07f, 0.98f, 0.85f, 0.20f, 0f);
        } else if (puntaje2 > puntaje1) {
            renderer.dibujarRect(-0.25f, 0.20f, 0.50f, 0.07f, 0.20f, 0.85f, 0.92f, 0f);
        } else {
            renderer.dibujarRect(-0.18f, 0.20f, 0.36f, 0.07f, 0.5f, 0.5f, 0.5f, 0f);
        }

        renderer.dibujarRect(-0.38f, 0.05f, 0.30f, 0.05f, 0.98f, 0.85f, 0.20f, 0f);
        renderer.dibujarRect(0.08f, 0.05f, 0.30f, 0.05f, 0.20f, 0.85f, 0.92f, 0f);

        renderer.dibujarRect(-0.38f, -0.08f, 0.30f, 0.03f, 0.98f, 0.85f, 0.20f, 0f);
        renderer.dibujarRect(0.08f, -0.08f, 0.30f, 0.03f, 0.20f, 0.85f, 0.92f, 0f);

        renderer.dibujarRect(0f, -0.15f, 0.50f, 0.05f, 0.7f, 0.7f, 0.7f, 0f);
    }

    /**
     * Actualiza el título de la ventana con información del juego.
     */
    private void actualizarTitulo() {
        int diff = pipeManager.getNivelDificultad();
        String titulo = String.format("Flappy Bird 2P | P1: %d  P2: %d | Nivel: %d",
            puntaje1, puntaje2, diff);

        if (estado == Estado.INICIO) {
            titulo += " | SPACE/UP: cada jugador se prepara";
        } else if (estado == Estado.ESPERANDO_JUGADORES) {
            StringBuilder ready = new StringBuilder();
            if (!p1Listo) ready.append("P1 ");
            if (!p2Listo) ready.append("P2 ");
            titulo += " | Esperando: " + ready.toString().trim();
        } else if (estado == Estado.GAME_OVER) {
            titulo += " | GAME OVER - SPACE/R para reiniciar";
        }

        GLFW.glfwSetWindowTitle(window, titulo);
    }

    /**
     * Bucle principal del juego.
     */
    private void loop() {
        float ultimoTiempo = (float) GLFW.glfwGetTime();

        while (!GLFW.glfwWindowShouldClose(window)) {
            float ahora = (float) GLFW.glfwGetTime();
            float dt = ahora - ultimoTiempo;
            ultimoTiempo = ahora;

            // Limitar dt para evitar "saltos" de física si el frame se congela.
            if (dt > 0.033f) {
                dt = 0.033f;
            }

            procesarInput();
            actualizar(dt);
            render();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }

    /**
     * Limpia recursos de OpenGL y GLFW.
     */
    private void cleanup() {
        renderer.cleanup();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    public static void main(String[] args) {
        new Game().run();
    }
}