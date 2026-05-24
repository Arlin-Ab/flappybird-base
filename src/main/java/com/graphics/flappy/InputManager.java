package com.graphics.flappy;

import org.lwjgl.glfw.GLFW;

/**
 * InputManager:
 * Encapsula la lectura de teclado mediante GLFW.
 *
 * Para cada tecla relevante guarda el estado del frame anterior, lo que
 * permite distinguir entre:
 *   - "Esta presionada ahora" (presionada): util para acciones continuas.
 *   - "Se acaba de presionar este frame" (justPresionada): util para acciones
 *     puntuales como saltar o confirmar, evitando que se disparen una y otra
 *     vez mientras el usuario mantiene la tecla apretada.
 *
 * Las teclas tracked se identifican por su constante GLFW_KEY_*. Como en
 * Java no se puede ampliar dinamicamente con buena perf un mapa, uso un
 * array indexado por keycode con un tamano fijo razonable.
 */
public class InputManager {

    // GLFW usa codigos hasta GLFW_KEY_LAST (~348). Reservo un poco mas por seguridad.
    private static final int MAX_KEYS = GLFW.GLFW_KEY_LAST + 1;

    private final long window;
    private final boolean[] estadoActual  = new boolean[MAX_KEYS];
    private final boolean[] estadoAnterior = new boolean[MAX_KEYS];

    public InputManager(long window) {
        this.window = window;
    }

    /**
     * Debe llamarse una vez por frame antes de consultar las teclas.
     * Mueve "estadoActual" a "estadoAnterior" y vuelve a leer el estado
     * actual desde GLFW.
     */
    public void actualizar() {
        for (int i = 0; i < MAX_KEYS; i++) {
            estadoAnterior[i] = estadoActual[i];
        }
        // Leo solo las teclas que uso para no recorrer 348 codigos.
        
        leerTecla(GLFW.GLFW_KEY_ESCAPE);
        leerTecla(GLFW.GLFW_KEY_SPACE);
        leerTecla(GLFW.GLFW_KEY_W);
        leerTecla(GLFW.GLFW_KEY_UP);
        leerTecla(GLFW.GLFW_KEY_R);
        leerTecla(GLFW.GLFW_KEY_ENTER);
        leerTecla(GLFW.GLFW_KEY_1);
        leerTecla(GLFW.GLFW_KEY_2);
        leerTecla(GLFW.GLFW_KEY_3);
    }

    private void leerTecla(int key) {
        estadoActual[key] = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    /** @return true si la tecla esta actualmente presionada. */
    public boolean presionada(int key) {
        return estadoActual[key];
    }

    /**
     * @return true SOLO en el frame en que la tecla paso de no presionada a
     *         presionada (deteccion de flanco). Ideal para "saltar".
     */
    public boolean justPresionada(int key) {
        return estadoActual[key] && !estadoAnterior[key];
    }
}
