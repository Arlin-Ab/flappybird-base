package com.graphics;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

import com.graphics.flappy.Game;
import com.graphics.flappy.InputManager;
import com.graphics.flappy.Renderer;
import com.graphics.flappy.SoundManager;

/**
 * AppFlappyBird:
 * Entry point del juego. Solo se encarga de:
 *   - Crear la ventana GLFW y el contexto OpenGL 3.3 core.
 *   - Construir el Renderer, InputManager y Game.
 *   - Correr el bucle principal (tiempo, input, update, render).
 *   - Liberar recursos al cerrar.
 *
 * Toda la logica del juego vive en el paquete com.graphics.flappy.
 */
public class AppFlappyBird {

    // Tamano inicial de la ventana (la ventana es redimensionable).
    private static final int ANCHO = 900;
    private static final int ALTO  = 700;

    private long window;
    private Renderer renderer;
    private InputManager input;
    private SoundManager sonido;
    private Game game;

    public void run() {
        init();
        loop();
        cleanup();
    }

    /**
     * Inicializa GLFW, crea la ventana y el contexto OpenGL, y construye los
     * subsistemas de juego.
     */
    private void init() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("No se pudo iniciar GLFW");
        }

        // Hints para crear un contexto OpenGL 3.3 core profile.
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(ANCHO, ALTO, "Flappy Bird OpenGL", 0, 0);
        if (window == 0) {
            throw new RuntimeException("No se pudo crear la ventana");
        }

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1); // VSync activado.
        GLFW.glfwShowWindow(window);

        // Cargar funciones de OpenGL (necesario antes de cualquier llamada GL).
        GL.createCapabilities();

        // Construir subsistemas.
        renderer = new Renderer();
        renderer.init();

        input = new InputManager(window);

        // SoundManager genera todos los sonidos en memoria con javax.sound.
        // Si el SO no tiene audio disponible queda en modo silencioso.
        sonido = new SoundManager();
        sonido.init();

        game = new Game(window, renderer, input, sonido);
    }

    /**
     * Bucle principal:
     *   1. Calcula dt (tiempo entre frames).
     *   2. Lee teclado (InputManager.actualizar()).
     *   3. Procesa input segun el estado (saltos, reinicio, etc.).
     *   4. Actualiza fisica y juego.
     *   5. Renderiza el frame.
     *   6. Hace swap de buffers y poll de eventos.
     */
    private void loop() {
        float ultimoTiempo = (float) GLFW.glfwGetTime();
        while (!GLFW.glfwWindowShouldClose(window)) {
            float ahora = (float) GLFW.glfwGetTime();
            float dt = ahora - ultimoTiempo;
            ultimoTiempo = ahora;
            // Limito dt para que un parpadeo (alt-tab, breakpoint) no haga saltos enormes.
            if (dt > 0.033f) dt = 0.033f;

            input.actualizar();
            game.procesarInput();
            game.actualizar(dt);
            game.render();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
            // El titulo lo refresca el propio Game cuando cambia algo relevante
            // (reset, puntaje, transicion de estado). No hace falta hacerlo aqui.
        }
    }

    /**
     * Libera todos los recursos GPU, las lineas de audio y termina GLFW.
     */
    private void cleanup() {
        if (renderer != null) renderer.cleanup();
        if (sonido != null)   sonido.cleanup();
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    public static void main(String[] args) {
        new AppFlappyBird().run();
    }
}
