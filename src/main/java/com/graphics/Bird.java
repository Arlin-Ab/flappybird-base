package com.graphics;

import org.lwjgl.opengl.GL11;

/**
 * Bird:
 * Representa un pájaro en el juego Flappy Bird.
 *
 * Responsabilidades:
 * - Manejar física (gravedad, impulso, velocidad).
 * - Dibujar figura compuesta (cuerpo, pico, ala animada, cola, ojo).
 * - Gestionar estado (vivo/muerto).
 *
 * El pájaro se inclina según su velocidad vertical para dar sensación de movimiento.
 */
public class Bird {
    private float x;
    private float y;
    private float velY;
    private final float ancho;
    private final float alto;
    private final float gravedad;
    private final float impulso;
    private final float velMaxCaida;
    private final float[] color;
    private final float initialY;

    private boolean vivo;
    private float tiempoAla;
    private static final float VELOCIDAD_ANGULAR = 2.5f;

    public Bird(float x, float y, float[] color) {
        this.x = x;
        this.y = y;
        this.initialY = y;
        this.velY = 0f;
        this.ancho = 0.10f;
        this.alto = 0.10f;
        this.gravedad = -1.9f;
        this.impulso = 0.85f;
        this.velMaxCaida = -1.8f;
        this.color = color;
        this.vivo = true;
        this.tiempoAla = 0f;
    }

    public void saltar() {
        if (vivo) {
            velY = impulso;
        }
    }

    public void actualizar(float dt) {
        if (!vivo) return;

        velY += gravedad * dt;
        if (velY < velMaxCaida) {
            velY = velMaxCaida;
        }
        y += velY * dt;

        tiempoAla += dt * 8f;

        float top = y + (alto * 0.5f);
        float bottom = y - (alto * 0.5f);
        if (top >= 1.0f || bottom <= -1.0f) {
            vivo = false;
        }
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getAncho() { return ancho; }
    public float getAlto() { return alto; }
    public boolean isVivo() { return vivo; }
    public float getVelY() { return velY; }
    public float[] getColor() { return color; }

    public void dibujar(Renderer renderer) {
        float angulo = velY * VELOCIDAD_ANGULAR;
        angulo = Math.max(-0.5f, Math.min(0.5f, angulo));

        float[] cuerpoColor = color;
        float[] picoColor = new float[]{0.92f, 0.65f, 0.20f};
        float[] alaColor = new float[]{color[0] * 0.8f, color[1] * 0.8f, color[2] * 0.8f};
        float[] ojoColor = new float[]{1f, 1f, 1f};
        float[] pupilaColor = new float[]{0f, 0f, 0f};

        renderer.dibujarCuadroCompuesto(x, y, ancho, alto, angulo,
            cuerpoColor, picoColor, alaColor, ojoColor, pupilaColor, tiempoAla);
    }

    public void setVivo(boolean vivo) {
        this.vivo = vivo;
    }

    public void reset() {
        this.y = initialY;
        this.velY = 0f;
        this.vivo = true;
        this.tiempoAla = 0f;
    }
}