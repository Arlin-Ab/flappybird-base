package com.graphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * PipeManager:
 * Gestiona la creación, movimiento y colisiones de las tuberías del juego.
 *
 * Responsabilidades:
 * - Spawn de nuevas tuberías con gap vertical aleatorio.
 * - Movimiento horizontal de tuberías.
 * - Detección de colisiones AABB.
 * - Puntuación cuando una tubería pasa al pájaro.
 * - Dificultad progresiva: aumenta velocidad y reduce espacio entre tuberías.
 */
public class PipeManager {
    private final List<Tuberia> tuberias;
    private final Random random;
    private final float velocidadBase;
    private final float tiempoEntreTuberiasBase;
    private final float velocidadMaxima;
    private final float tiempoMinimo;
    private final float gapMinimo;

    private float velocidadActual;
    private float tiempoEntreTuberiasActual;
    private float timerSpawn;
    private int nivelDificultad;
    private boolean activo;
    private float gapActual;

    private final float GAP_ALTO = 0.48f;
    private final float TUBERIA_ANCHO = 0.18f;
    private final float GAP_MIN_CENTRO = -0.45f;
    private final float GAP_MAX_CENTRO = 0.45f;

    public PipeManager() {
        this.tuberias = new ArrayList<>();
        this.random = new Random();
        this.velocidadBase = 0.62f;
        this.tiempoEntreTuberiasBase = 1.5f;
        this.velocidadMaxima = 1.4f;
        this.tiempoMinimo = 0.7f;
        this.gapMinimo = 0.32f;
        this.velocidadActual = velocidadBase;
        this.tiempoEntreTuberiasActual = tiempoEntreTuberiasBase;
        this.timerSpawn = 0f;
        this.nivelDificultad = 1;
        this.activo = false;
        this.gapActual = GAP_ALTO;
    }

    public void activar() {
        this.activo = true;
        this.timerSpawn = 0f;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void actualizar(float dt) {
        if (!activo) return;

        timerSpawn += dt;
        if (timerSpawn >= tiempoEntreTuberiasActual) {
            timerSpawn = 0f;
            spawnTuberia();
        }

        Iterator<Tuberia> it = tuberias.iterator();
        while (it.hasNext()) {
            Tuberia t = it.next();
            t.x -= velocidadActual * dt;

            if (t.x + (TUBERIA_ANCHO * 0.5f) < -1.3f) {
                it.remove();
            }
        }
    }

    private void spawnTuberia() {
        float gapMitad = gapActual * 0.5f;
        float minCentro = Math.max(GAP_MIN_CENTRO + gapMitad, -0.9f + gapMitad);
        float maxCentro = Math.min(GAP_MAX_CENTRO - gapMitad, 0.9f - gapMitad);
        float gapCentro = minCentro + random.nextFloat() * (maxCentro - minCentro);
        tuberias.add(new Tuberia(1.2f, gapCentro));
    }

    public boolean colisionaConBird(Bird bird) {
        if (!bird.isVivo()) return false;

        float birdLeft = bird.getX() - (bird.getAncho() * 0.5f);
        float birdRight = bird.getX() + (bird.getAncho() * 0.5f);
        float birdBottom = bird.getY() - (bird.getAlto() * 0.5f);
        float birdTop = bird.getY() + (bird.getAlto() * 0.5f);

        for (Tuberia t : tuberias) {
            float pipeLeft = t.x - (TUBERIA_ANCHO * 0.5f);
            float pipeRight = t.x + (TUBERIA_ANCHO * 0.5f);
            boolean overlapX = birdRight > pipeLeft && birdLeft < pipeRight;
            if (!overlapX) continue;

            float gapTop = t.gapCentroY + (gapActual * 0.5f);
            float gapBottom = t.gapCentroY - (gapActual * 0.5f);
            if (birdTop > gapTop || birdBottom < gapBottom) {
                return true;
            }
        }
        return false;
    }

    public int revisarPuntuacion(Bird bird) {
        int puntos = 0;
        for (Tuberia t : tuberias) {
            if (!t.puntuada && t.x + (TUBERIA_ANCHO * 0.5f) < bird.getX()) {
                t.puntuada = true;
                puntos++;
            }
        }
        return puntos;
    }

    public void aumentarDificultad(int puntos) {
        nivelDificultad = 1 + (puntos / 3);
        float progreso = Math.min(puntos / 30f, 1f);
        velocidadActual = velocidadBase + (velocidadMaxima - velocidadBase) * progreso;
        tiempoEntreTuberiasActual = tiempoEntreTuberiasBase - (tiempoEntreTuberiasBase - tiempoMinimo) * progreso;
        gapActual = GAP_ALTO - (GAP_ALTO - gapMinimo) * progreso;
    }

    public int getNivelDificultad() { return nivelDificultad; }
    public float getVelocidadActual() { return velocidadActual; }
    public List<Tuberia> getTuberias() { return tuberias; }
    public float getGapAlto() { return gapActual; }
    public float getTuberiaAncho() { return TUBERIA_ANCHO; }

    public void reset() {
        tuberias.clear();
        timerSpawn = 0f;
        velocidadActual = velocidadBase;
        tiempoEntreTuberiasActual = tiempoEntreTuberiasBase;
        nivelDificultad = 1;
        activo = false;
        gapActual = GAP_ALTO;
    }

    public static class Tuberia {
        public float x;
        public float gapCentroY;
        public boolean puntuada;

        public Tuberia(float x, float gapCentroY) {
            this.x = x;
            this.gapCentroY = gapCentroY;
        }
    }
}