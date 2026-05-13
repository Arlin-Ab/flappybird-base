package com.graphics;

/**
 * Bird:
 * Representa un pájaro del juego Flappy Bird.
 * Maneja la física vertical (gravedad + impulso de salto),
 * el estado de vida (vivo/muerto), el dibujo compuesto por primitivas OpenGL
 * y la animación de aleteo.
 *
 * El pájaro está compuesto por las siguientes partes dibujadas con primitivas:
 *  - Cuerpo: elipse (GL_TRIANGLE_FAN escalado).
 *  - Pico: triángulo apuntando a la derecha (GL_TRIANGLES).
 *  - Ala: rectángulo con pivote inferior, animado en bucle (senoidal).
 *  - Cola: triángulo apuntando a la izquierda (rotado 180°).
 *  - Ojo: círculo blanco con pupila negra (elipses concéntricas).
 *
 * El pájaro se inclina según su velocidad vertical (rotación en Z).
 */
public class Bird {

    // ─── Constantes de dibujo (tamaños relativos) ───────────────────────────
    /** Semieje horizontal del cuerpo (elipse). */
    private static final float RADIO_X_CUERPO = 0.08f;
    /** Semieje vertical del cuerpo (elipse). */
    private static final float RADIO_Y_CUERPO = 0.07f;

    /** Ancho del pico. */
    private static final float ANCHO_PICO = 0.04f;
    /** Alto del pico. */
    private static final float ALTO_PICO = 0.03f;

    /** Ancho del ala. */
    private static final float ANCHO_ALA = 0.05f;
    /** Largo del ala (desde pivote hasta punta). */
    private static final float ALTO_ALA = 0.09f;
    /** Offset horizontal del ala respecto al centro del cuerpo. */
    private static final float ALA_OFFSET_X = 0.0f;
    /** Offset vertical del ala respecto al centro del cuerpo. */
    private static final float ALA_OFFSET_Y = 0.03f;

    /** Ancho de la cola. */
    private static final float ANCHO_COLA = 0.025f;
    /** Alto de la cola. */
    private static final float ALTO_COLA = 0.04f;
    /** Offset horizontal de la cola (a la izquierda del cuerpo). */
    private static final float COLA_OFFSET_X = -0.08f;
    /** Offset vertical de la cola. */
    private static final float COLA_OFFSET_Y = 0.01f;

    /** Radio del ojo (blanco). */
    private static final float RADIO_OJO = 0.018f;
    /** Radio de la pupila (negro dentro del ojo). */
    private static final float RADIO_PUPILA = 0.008f;
    /** Offset horizontal del ojo respecto al centro. */
    private static final float OJO_OFFSET_X = 0.025f;
    /** Offset vertical del ojo respecto al centro. */
    private static final float OJO_OFFSET_Y = 0.02f;

    // ─── Física ─────────────────────────────────────────────────────────────
    /** Posición horizontal fija del pájaro en NDC. */
    private float x;
    /** Posición vertical actual en NDC. */
    private float y;
    /** Velocidad vertical actual. */
    private float velY;
    /** Si el pájaro sigue vivo (no ha chocado). */
    private boolean vivo;
    /** Puntuación individual del jugador. */
    private int puntaje;

    // ─── Animación ──────────────────────────────────────────────────────────
    /** Contador de tiempo acumulado para la animación de aleteo. */
    private float tiempoAnimacion;
    /** Ángulo actual del ala (calculado con seno en cada frame). */
    private float anguloAla;

    // ─── Colores del pájaro ─────────────────────────────────────────────────
    /** Color del cuerpo (R, G, B). */
    private final float[] colorCuerpo;
    /** Color del ala. */
    private final float[] colorAla;
    /** Color del pico. */
    private final float[] colorPico;
    /** Color de la cola. */
    private final float[] colorCola;

    // ─── Constantes de animación ────────────────────────────────────────────
    /** Velocidad angular del aleteo (radianes por segundo). */
    private static final float VELOCIDAD_ALETEO = 18.0f;
    /** Amplitud máxima del aleteo en radianes. */
    private static final float AMPLITUD_ALETEO = 0.6f;
    /** Ángulo de reposo del ala cuando no se está jugando. */
    private static final float ANGULO_REPOSO = 0.3f;

    // ═══════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Crea un nuevo pájaro con la posición y colores especificados.
     *
     * @param x           Posición horizontal fija en NDC (ej: -0.35 para jugador 1).
     * @param colorCuerpo Color RGB del cuerpo.
     * @param colorAla    Color RGB del ala.
     * @param colorPico   Color RGB del pico.
     * @param colorCola   Color RGB de la cola.
     */
    public Bird(float x, float[] colorCuerpo, float[] colorAla,
                float[] colorPico, float[] colorCola) {
        this.x = x;
        this.colorCuerpo = colorCuerpo;
        this.colorAla = colorAla;
        this.colorPico = colorPico;
        this.colorCola = colorCola;
        this.vivo = true;
        this.y = 0.0f;
        this.velY = 0.0f;
        this.puntaje = 0;
        this.tiempoAnimacion = 0.0f;
        this.anguloAla = ANGULO_REPOSO;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS PÚBLICOS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Aplica un impulso hacia arriba (salto).
     *
     * @param impulso Magnitud del impulso vertical.
     */
    public void saltar(float impulso) {
        if (vivo) {
            velY = impulso;
        }
    }

    /**
     * Actualiza la física del pájaro: aplica gravedad, integra velocidad
     * en posición y actualiza la animación de aleteo.
     *
     * @param dt       Delta time en segundos.
     * @param gravedad Aceleración de gravedad (negativa = hacia abajo).
     */
    public void actualizar(float dt, float gravedad) {
        if (!vivo) {
            // Si está muerto, seguir cayendo por inercia.
            velY += gravedad * dt;
            y += velY * dt;
            return;
        }

        // Integración de Euler simple:
        //   velocidad += aceleración * dt
        //   posición  += velocidad * dt
        velY += gravedad * dt;
        y += velY * dt;

        // Actualizar animación de aleteo.
        // Usamos una función senoidal para un movimiento natural de subida y bajada.
        tiempoAnimacion += dt;
        anguloAla = (float) Math.sin(tiempoAnimacion * VELOCIDAD_ALETEO) * AMPLITUD_ALETEO;
    }

    /**
     * Calcula el ángulo de inclinación del pájaro basado en su velocidad vertical.
     * Cuando cae rápido, se inclina hacia abajo (rotación negativa).
     * Cuando sube, se inclina hacia arriba (rotación positiva).
     *
     * @return Ángulo de inclinación en radianes.
     */
    public float getAnguloInclinacion() {
        // Mapear velocidad vertical a ángulo.
        // velY positiva = subiendo → ángulo positivo (nariz arriba).
        // velY negativa = cayendo  → ángulo negativo (nariz abajo).
        float angulo = velY * 0.6f;

        // Limitar inclinación entre -80° y +30° aproximadamente.
        if (angulo > 0.5f)  angulo = 0.5f;
        if (angulo < -1.3f) angulo = -1.3f;

        return angulo;
    }

    /**
     * Dibuja el pájaro completo usando las primitivas del Renderer.
     * Todas las partes se dibujan relativas al centro del cuerpo (x, y)
     * y se les aplica la rotación de inclinación.
     *
     * @param renderer Renderer con los métodos de dibujo.
     */
    public void dibujar(Renderer renderer) {
        float inclinacion = getAnguloInclinacion();

        // ── Cuerpo (elipse) ──
        // El cuerpo se dibuja como una elipse centrada en (x, y).
        // Usamos triangle fan escalado de forma no uniforme para la elipse.
        // La rotación de inclinación afecta a todo el cuerpo.
        float cx = x;
        float cy = y;
        renderer.dibujarElipse(cx, cy, RADIO_X_CUERPO, RADIO_Y_CUERPO,
                colorCuerpo[0], colorCuerpo[1], colorCuerpo[2], inclinacion);

        // ── Pico (triángulo a la derecha) ──
        // El pico se posiciona a la derecha del cuerpo.
        // Calculamos su posición absoluta teniendo en cuenta la inclinación.
        float picoLocalX = RADIO_X_CUERPO + ANCHO_PICO * 0.3f;
        float picoLocalY = 0.0f;
        float picoX = cx + picoLocalX * (float) Math.cos(inclinacion)
                         - picoLocalY * (float) Math.sin(inclinacion);
        float picoY = cy + picoLocalX * (float) Math.sin(inclinacion)
                         + picoLocalY * (float) Math.cos(inclinacion);
        renderer.dibujarTriangulo(picoX, picoY, ANCHO_PICO, ALTO_PICO,
                colorPico[0], colorPico[1], colorPico[2], inclinacion);

        // ── Cola (triángulo a la izquierda, rotado 180°) ──
        float colaLocalX = COLA_OFFSET_X;
        float colaLocalY = COLA_OFFSET_Y;
        float colaX = cx + colaLocalX * (float) Math.cos(inclinacion)
                         - colaLocalY * (float) Math.sin(inclinacion);
        float colaY = cy + colaLocalX * (float) Math.sin(inclinacion)
                         + colaLocalY * (float) Math.cos(inclinacion);
        // PI radianes = 180° para que apunte hacia la izquierda.
        renderer.dibujarTriangulo(colaX, colaY, ANCHO_COLA, ALTO_COLA,
                colorCola[0], colorCola[1], colorCola[2],
                inclinacion + (float) Math.PI);

        // ── Ala (animada con aleteo) ──
        // El ala rota alrededor de su pivote inferior (unión al cuerpo).
        // Se combina la inclinación del pájaro con el ángulo de aleteo.
        float alaLocalX = ALA_OFFSET_X;
        float alaLocalY = ALA_OFFSET_Y;
        float alaX = cx + alaLocalX * (float) Math.cos(inclinacion)
                        - alaLocalY * (float) Math.sin(inclinacion);
        float alaY = cy + alaLocalX * (float) Math.sin(inclinacion)
                        + alaLocalY * (float) Math.cos(inclinacion);
        float alaRotacionTotal = inclinacion + anguloAla;
        renderer.dibujarAla(alaX, alaY, ANCHO_ALA, ALTO_ALA,
                colorAla[0], colorAla[1], colorAla[2], alaRotacionTotal);

        // ── Ojo (círculo blanco) ──
        float ojoLocalX = OJO_OFFSET_X;
        float ojoLocalY = OJO_OFFSET_Y;
        float ojoX = cx + ojoLocalX * (float) Math.cos(inclinacion)
                        - ojoLocalY * (float) Math.sin(inclinacion);
        float ojoY = cy + ojoLocalX * (float) Math.sin(inclinacion)
                        + ojoLocalY * (float) Math.cos(inclinacion);
        renderer.dibujarElipse(ojoX, ojoY, RADIO_OJO, RADIO_OJO,
                1.0f, 1.0f, 1.0f);

        // ── Pupila (círculo negro más pequeño) ──
        renderer.dibujarElipse(ojoX, ojoY, RADIO_PUPILA, RADIO_PUPILA,
                0.0f, 0.0f, 0.0f);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GETTERS Y SETTERS
    // ═══════════════════════════════════════════════════════════════════════

    /** @return Posición horizontal del pájaro. */
    public float getX() { return x; }

    /** @return Posición vertical del pájaro. */
    public float getY() { return y; }

    /** @param y Nueva posición vertical. */
    public void setY(float y) { this.y = y; }

    /** @return Velocidad vertical actual. */
    public float getVelY() { return velY; }

    /** @param velY Nueva velocidad vertical. */
    public void setVelY(float velY) { this.velY = velY; }

    /** @return true si el pájaro está vivo. */
    public boolean isVivo() { return vivo; }

    /** Marca al pájaro como muerto (colisionado). */
    public void morir() { this.vivo = false; }

    /** @return Puntuación actual. */
    public int getPuntaje() { return puntaje; }

    /** Incrementa la puntuación en 1. */
    public void sumarPuntaje() { this.puntaje++; }

    /**
     * Reinicia el pájaro a su estado inicial (vivo, posición centrada,
     * velocidad cero, puntaje cero).
     */
    public void reiniciar() {
        y = 0.0f;
        velY = 0.0f;
        vivo = true;
        puntaje = 0;
        tiempoAnimacion = 0.0f;
        anguloAla = ANGULO_REPOSO;
    }

    /**
     * Obtiene el rectángulo de colisión (AABB) del pájaro.
     * Se usa para detectar colisiones con tuberías y límites de pantalla.
     *
     * @return Array con [left, right, bottom, top] en NDC.
     */
    public float[] getAABB() {
        float halfW = RADIO_X_CUERPO * 0.85f; // margen interno para colisión más justa
        float halfH = RADIO_Y_CUERPO * 0.85f;
        return new float[] {
            x - halfW,   // left
            x + halfW,   // right
            y - halfH,   // bottom
            y + halfH    // top
        };
    }
}
