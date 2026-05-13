package com.graphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * PipeManager:
 * Gestión de la lógica de tuberías (obstáculos) y colisiones.
 *
 * Responsabilidades:
 *  - Generar nuevas tuberías a intervalos configurados.
 *  - Mover las tuberías de derecha a izquierda.
 *  - Detectar colisiones entre tuberías y pájaros.
 *  - Verificar cuándo un pájaro supera una tubería (para puntuación).
 *  - Ajustar la dificultad (velocidad y frecuencia) según el puntaje total.
 *  - Dibujar todas las tuberías activas.
 */
public class PipeManager {

    // ─── Constantes geométricas de tuberías ──────────────────────────────────
    /** Ancho de cada tubería en NDC. */
    private static final float TUBERIA_ANCHO = 0.18f;
    /** Altura del hueco (gap) entre tubería superior e inferior. */
    private static final float GAP_ALTO = 0.48f;
    /** Límite inferior del centro del gap (más abajo). */
    private static final float GAP_MIN_CENTRO = -0.45f;
    /** Límite superior del centro del gap (más arriba). */
    private static final float GAP_MAX_CENTRO = 0.45f;
    /** Ancho del "borde" decorativo en los extremos de las tuberías. */
    private static final float BORDE_ANCHO = TUBERIA_ANCHO + 0.04f;
    /** Alto del borde decorativo. */
    private static final float BORDE_ALTO = 0.04f;

    // ─── Constantes de dificultad ───────────────────────────────────────────
    /** Velocidad base de las tuberías (unidades NDC por segundo). */
    private static final float VELOCIDAD_BASE = 0.5f;
    /** Tiempo base entre spawn de tuberías (segundos). */
    private static final float TIEMPO_SPAWN_BASE = 1.5f;
    /** Velocidad máxima (límite para mantener jugabilidad). */
    private static final float VELOCIDAD_MAXIMA = 1.55f;
    /** Tiempo mínimo entre spawns (límite inferior). */
    private static final float TIEMPO_SPAWN_MINIMO = 0.55f;
    /** Incremento de velocidad por cada punto de dificultad. */
    private static final float INCREMENTO_VELOCIDAD = 0.05f;
    /** Decremento de tiempo de spawn por cada punto de dificultad. */
    private static final float DECREMENTO_TIEMPO = 0.05f;
    /** Puntos necesarios para subir un nivel de dificultad. */
    private static final int PUNTOS_POR_NIVEL = 5;

    // ─── Estado interno ─────────────────────────────────────────────────────
    /** Lista de tuberías activas en pantalla. */
    private final List<Tuberia> tuberias;
    /** Generador de números aleatorios para variar el gap. */
    private final Random random;
    /** Temporizador acumulado para controlar el spawn. */
    private float timerSpawn;
    /** Velocidad actual de las tuberías (aumenta con la dificultad). */
    private float velocidadActual;
    /** Tiempo actual entre spawns (disminuye con la dificultad). */
    private float tiempoSpawnActual;
    /** Nivel de dificultad actual (0, 1, 2, ...). */
    private int nivelDificultad;

    // ─── Colores ────────────────────────────────────────────────────────────
    /** Color del cuerpo de la tubería (verde). */
    private static final float[] COLOR_TUBERIA = {0.18f, 0.80f, 0.44f};
    /** Color del borde de la tubería (verde más oscuro). */
    private static final float[] COLOR_BORDE = {0.10f, 0.60f, 0.30f};

    // ═══════════════════════════════════════════════════════════════════════
    // CLASE INTERNA: Tuberia
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Modelo de una tubería individual.
     * Cada tubería tiene una posición horizontal común para ambas mitades
     * (superior e inferior) y un centro vertical del hueco (gap).
     * Lleva dos flags de puntuación, uno por cada jugador,
     * para evitar contar la misma tubería dos veces.
     */
    public static class Tuberia {
        /** Posición horizontal del centro de la tubería. */
        float x;
        /** Centro vertical del hueco entre tubería superior e inferior. */
        float gapCentroY;
        /** Si el jugador 1 ya fue puntuado por esta tubería. */
        boolean puntuadaJ1;
        /** Si el jugador 2 ya fue puntuado por esta tubería. */
        boolean puntuadaJ2;

        Tuberia(float x, float gapCentroY) {
            this.x = x;
            this.gapCentroY = gapCentroY;
            this.puntuadaJ1 = false;
            this.puntuadaJ2 = false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Inicializa el gestor de tuberías con dificultad base.
     */
    public PipeManager() {
        this.tuberias = new ArrayList<>();
        this.random = new Random();
        this.velocidadActual = VELOCIDAD_BASE;
        this.tiempoSpawnActual = TIEMPO_SPAWN_BASE;
        this.timerSpawn = 0.0f;
        this.nivelDificultad = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACTUALIZACIÓN DE LÓGICA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Actualiza el estado de las tuberías: las mueve hacia la izquierda,
     * genera nuevas cuando corresponde, elimina las que salen de pantalla
     * y verifica puntuaciones y colisiones para ambos jugadores.
     *
     * @param dt       Delta time en segundos.
     * @param bird1    Pájaro del jugador 1 (para colisiones y puntuación).
     * @param bird2    Pájaro del jugador 2 (para colisiones y puntuación).
     */
    public void actualizar(float dt, Bird bird1, Bird bird2) {
        // Incrementar temporizador de spawn.
        timerSpawn += dt;

        // Generar nueva tubería si el temporizador supera el umbral.
        if (timerSpawn >= tiempoSpawnActual) {
            timerSpawn = 0.0f;
            spawnTuberia();
        }

        Iterator<Tuberia> it = tuberias.iterator();
        while (it.hasNext()) {
            Tuberia t = it.next();

            // Mover la tubería hacia la izquierda.
            t.x -= velocidadActual * dt;

            // Verificar si el jugador 1 ya pasó esta tubería (para puntuación).
            if (!t.puntuadaJ1 && bird1.isVivo()) {
                if (t.x + (TUBERIA_ANCHO * 0.5f) < bird1.getX()) {
                    t.puntuadaJ1 = true;
                    bird1.sumarPuntaje();
                }
            }

            // Verificar si el jugador 2 ya pasó esta tubería.
            if (!t.puntuadaJ2 && bird2.isVivo()) {
                if (t.x + (TUBERIA_ANCHO * 0.5f) < bird2.getX()) {
                    t.puntuadaJ2 = true;
                    bird2.sumarPuntaje();
                }
            }

            // Detectar colisiones con cada pájaro vivo.
            if (bird1.isVivo() && colisionaCon(t, bird1)) {
                bird1.morir();
            }
            if (bird2.isVivo() && colisionaCon(t, bird2)) {
                bird2.morir();
            }

            // Eliminar tuberías que ya salieron completamente de pantalla.
            // Esto evita acumular objetos innecesarios en memoria.
            if (t.x + (TUBERIA_ANCHO * 0.5f) < -1.3f) {
                it.remove();
            }
        }
    }

    /**
     * Genera una nueva tubería en el borde derecho de la pantalla
     * con una posición vertical aleatoria para el gap.
     */
    private void spawnTuberia() {
        float gapCentro = GAP_MIN_CENTRO
            + random.nextFloat() * (GAP_MAX_CENTRO - GAP_MIN_CENTRO);
        tuberias.add(new Tuberia(1.25f, gapCentro));
    }

    /**
     * Verifica colisión AABB entre una tubería y un pájaro.
     *
     * Primero comprueba si hay solapamiento horizontal.
     * Si lo hay, verifica si el pájaro está fuera del gap vertical.
     *
     * @param t    Tubería a comprobar.
     * @param bird Pájaro a comprobar.
     * @return true si hay colisión.
     */
    public boolean colisionaCon(Tuberia t, Bird bird) {
        float[] aabb = bird.getAABB();
        float birdLeft   = aabb[0];
        float birdRight  = aabb[1];
        float birdBottom = aabb[2];
        float birdTop    = aabb[3];

        // Límites horizontales de la tubería.
        float pipeLeft  = t.x - (TUBERIA_ANCHO * 0.5f);
        float pipeRight = t.x + (TUBERIA_ANCHO * 0.5f);

        // ¿Hay solapamiento en X?
        boolean overlapX = birdRight > pipeLeft && birdLeft < pipeRight;
        if (!overlapX) {
            return false;
        }

        // Límites verticales del gap.
        float gapTop    = t.gapCentroY + (GAP_ALTO * 0.5f);
        float gapBottom = t.gapCentroY - (GAP_ALTO * 0.5f);

        // Colisiona si el pájaro está por encima del gap o por debajo.
        return birdTop > gapTop || birdBottom < gapBottom;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIFICULTAD PROGRESIVA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Actualiza la dificultad basándose en el puntaje combinado de ambos jugadores.
     * A mayor puntuación:
     *  - Aumenta la velocidad de las tuberías.
     *  - Disminuye el tiempo entre spawns (más tuberías por minuto).
     *
     * La dificultad tiene un límite máximo para mantener la jugabilidad.
     *
     * @param puntajeTotal Suma de puntajes de ambos jugadores.
     */
    public void actualizarDificultad(int puntajeTotal) {
        // Calcular nivel según puntuación total.
        int nuevoNivel = puntajeTotal / PUNTOS_POR_NIVEL;

        if (nuevoNivel != nivelDificultad) {
            nivelDificultad = nuevoNivel;

            // Aumentar velocidad con cada nivel.
            velocidadActual = VELOCIDAD_BASE + (nivelDificultad * INCREMENTO_VELOCIDAD);
            if (velocidadActual > VELOCIDAD_MAXIMA) {
                velocidadActual = VELOCIDAD_MAXIMA;
            }

            // Reducir tiempo entre spawns con cada nivel.
            tiempoSpawnActual = TIEMPO_SPAWN_BASE - (nivelDificultad * DECREMENTO_TIEMPO);
            if (tiempoSpawnActual < TIEMPO_SPAWN_MINIMO) {
                tiempoSpawnActual = TIEMPO_SPAWN_MINIMO;
            }
        }
    }

    /** @return Nivel de dificultad actual. */
    public int getNivelDificultad() {
        return nivelDificultad;
    }

    /** @return Velocidad actual de tuberías. */
    public float getVelocidadActual() {
        return velocidadActual;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIBUJO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Dibuja todas las tuberías activas usando el Renderer.
     * Cada tubería se compone de:
     *  - Tramo superior (desde el gap hasta el techo).
     *  - Tramo inferior (desde el suelo hasta el gap).
     *  - Bordes decorativos en los extremos del gap.
     *
     * @param renderer Renderer con los métodos de dibujo.
     */
    public void dibujar(Renderer renderer) {
        for (Tuberia t : tuberias) {
            float gapTop    = t.gapCentroY + (GAP_ALTO * 0.5f);
            float gapBottom = t.gapCentroY - (GAP_ALTO * 0.5f);

            // ── Tramo superior de tubería ──
            // Va desde gapTop hasta el techo de la pantalla (y = 1.0).
            float altoSuperior = 1.0f - gapTop;
            if (altoSuperior > 0.0f) {
                float yCentroSup = gapTop + (altoSuperior * 0.5f);
                renderer.dibujarRectangulo(t.x, yCentroSup, TUBERIA_ANCHO, altoSuperior,
                    COLOR_TUBERIA[0], COLOR_TUBERIA[1], COLOR_TUBERIA[2], 0.0f);

                // Borde decorativo en el extremo inferior del tramo superior.
                renderer.dibujarRectangulo(t.x, gapTop - BORDE_ALTO * 0.5f,
                    BORDE_ANCHO, BORDE_ALTO,
                    COLOR_BORDE[0], COLOR_BORDE[1], COLOR_BORDE[2], 0.0f);
            }

            // ── Tramo inferior de tubería ──
            // Va desde el suelo (y = -1.0) hasta gapBottom.
            float altoInferior = gapBottom + 1.0f;
            if (altoInferior > 0.0f) {
                float yCentroInf = -1.0f + (altoInferior * 0.5f);
                renderer.dibujarRectangulo(t.x, yCentroInf, TUBERIA_ANCHO, altoInferior,
                    COLOR_TUBERIA[0], COLOR_TUBERIA[1], COLOR_TUBERIA[2], 0.0f);

                // Borde decorativo en el extremo superior del tramo inferior.
                renderer.dibujarRectangulo(t.x, gapBottom + BORDE_ALTO * 0.5f,
                    BORDE_ANCHO, BORDE_ALTO,
                    COLOR_BORDE[0], COLOR_BORDE[1], COLOR_BORDE[2], 0.0f);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // REINICIO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Reinicia el gestor de tuberías a su estado inicial.
     * Limpia todas las tuberías activas y restablece la dificultad base.
     */
    public void reiniciar() {
        tuberias.clear();
        timerSpawn = 0.0f;
        velocidadActual = VELOCIDAD_BASE;
        tiempoSpawnActual = TIEMPO_SPAWN_BASE;
        nivelDificultad = 0;
    }
}
