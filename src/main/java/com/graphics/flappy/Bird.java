package com.graphics.flappy;

/**
 * Bird:
 * Representa a un pajaro del juego. Cada jugador tiene su propia instancia.
 *
 * Encapsula:
 *  - Estado fisico (posicion Y, velocidad vertical, vivo/muerto).
 *  - Estado de partida (puntaje individual).
 *  - Animacion del aleteo del ala.
 *  - Renderizado compuesto por varias primitivas (cuerpo, pico, ala, cola, ojo).
 *
 * Convencion:
 *  - X es fijo (posicion horizontal del jugador, dada al construir).
 *  - Y va de -1 (suelo) a +1 (techo) en NDC.
 *  - rotacionVisual depende de la velocidad vertical: sube -> inclina arriba,
 *    cae -> inclina abajo. No afecta la fisica ni la colision AABB.
 */
public class Bird {

    // ---- Constantes de fisica ----
    private static final float GRAVEDAD = -1.9f;
    private static final float IMPULSO_SALTO = 0.85f;
    private static final float VELOCIDAD_MAX_CAIDA = -1.8f;

    // ---- Caja de colision (AABB) ----
    // Se usa para el calculo de choque contra tuberias. Es algo mas chica que
    // el dibujo para que el juego sea mas permisivo y justo.
    public static final float ANCHO_COLISION = 0.09f;
    public static final float ALTO_COLISION  = 0.08f;

    // ---- Limites verticales del mundo ----
    // Estos valores deben coincidir con el dibujo del suelo y del techo (cielo)
    // para que el pajaro no atraviese el grafico antes de morir.
    // Y_SUELO = tope visual del pasto. Y_TECHO = borde superior de NDC.
    public static final float Y_SUELO = -0.80f;
    public static final float Y_TECHO =  1.00f;

    // ---- Tamanos visuales (del dibujo, no de la colision) ----
    private static final float RADIO_CUERPO = 0.050f;
    private static final float RADIO_OJO    = 0.015f;
    private static final float RADIO_PUPILA = 0.007f;

    // ---- Posicion y velocidad ----
    private final float x;       // X fija del jugador en NDC.
    private float y;             // Y actual del pajaro.
    private float velY;          // velocidad vertical.

    // ---- Estado ----
    private boolean vivo;
    // Una vez muerto, sigue cayendo por gravedad hasta tocar el suelo. Cuando
    // toca el suelo, enReposo = true y deja de moverse.
    private boolean enReposo;
    // Si murio por tuberia EN MODO 2 JUGADORES y el otro sigue vivo, queremos
    // que el pajaro caiga normalmente por gravedad y al tocar el piso se
    // OCULTE de la pantalla (deja de renderizarse). Asi el muerto no estorba
    // al que sigue jugando. Se setea en matar(true) y se resetea en reset().
    private boolean ocultarAlTocarSuelo;
    // Una vez oculto, render() retorna sin dibujar nada.
    private boolean oculto;
    private int puntaje;

    // ---- Animacion ----
    private float timerAla;      // avanza con dt para oscilar el ala.

    // ---- Colores principales ----
    // Cada jugador puede usar su propia paleta para diferenciarse visualmente.
    private final float cuerpoR, cuerpoG, cuerpoB;
    private final float alaR, alaG, alaB;

    /**
     * Construye un pajaro en la posicion X dada, con su paleta de color.
     * @param x         posicion horizontal fija en NDC.
     * @param cuerpoR..  color principal del cuerpo, cola y ala primaria.
     * @param alaR..     color secundario para el ala (mas oscuro/contraste).
     */
    public Bird(float x,
                float cuerpoR, float cuerpoG, float cuerpoB,
                float alaR, float alaG, float alaB) {
        this.x = x;
        this.cuerpoR = cuerpoR;
        this.cuerpoG = cuerpoG;
        this.cuerpoB = cuerpoB;
        this.alaR = alaR;
        this.alaG = alaG;
        this.alaB = alaB;
        reset();
    }

    /**
     * Reinicia el estado para una nueva partida con Y inicial dada.
     */
    public void reset(float yInicial) {
        this.y = yInicial;
        this.velY = 0.0f;
        this.vivo = true;
        this.enReposo = false;
        this.ocultarAlTocarSuelo = false;
        this.oculto = false;
        this.puntaje = 0;
        this.timerAla = 0.0f;
    }

    /**
     * Atajo: reinicia el pajaro en el centro vertical (Y = 0).
     */
    public void reset() {
        reset(0.0f);
    }

    // ============================================================
    // Logica de actualizacion
    // ============================================================

    /**
     * Aplica gravedad e integra la posicion.
     *
     * - Si esta vivo: gravedad, integracion, choque contra techo/suelo (muere).
     * - Si esta muerto pero no en reposo: sigue cayendo por gravedad hasta que
     *   toque el suelo. Asi el pajaro no queda flotando en el aire al morir.
     * - Si esta en reposo (caido en el suelo): no hace nada.
     */
    public void actualizar(float dt) {
        if (enReposo) return;

        // El ala solo aletea mientras esta vivo; al morir se congela.
        if (vivo) {
            timerAla += dt * 14.0f;
        }

        // Gravedad e integracion (se aplica tanto vivo como muerto-cayendo).
        velY += GRAVEDAD * dt;
        if (velY < VELOCIDAD_MAX_CAIDA) {
            velY = VELOCIDAD_MAX_CAIDA;
        }
        y += velY * dt;

        // Colision con techo y suelo. Y_SUELO esta alineado con el tope del
        // pasto dibujado.
        float top = y + (ALTO_COLISION * 0.5f);
        float bottom = y - (ALTO_COLISION * 0.5f);
        if (top >= Y_TECHO) {
            y = Y_TECHO - (ALTO_COLISION * 0.5f);
            vivo = false;
            velY = 0.0f;
            // No se queda en reposo: rebota hacia abajo cayendo de nuevo.
        }

        if (bottom <= Y_SUELO) {
            y = Y_SUELO + (ALTO_COLISION * 0.5f);
            vivo = false;
            velY = 0.0f;
            enReposo = true;
            // Si fue marcado como "ocultable" al morir (otro pajaro sigue
            // jugando), al tocar el piso lo desaparezco para no estorbar.
            if (ocultarAlTocarSuelo) {
                oculto = true;
            }
        }
    }

    /**
     * Aplica el impulso de salto. Tambien dispara un pequeno "boost" del ala
     * para que el aleteo se vea sincronizado con el salto.
     */
    public void saltar() {
        if (!vivo) return;
        velY = IMPULSO_SALTO;
        // Avance manual del timer para que la onda del seno haga un flap visible.
        timerAla += 1.4f;
    }

    /**
     * Marca al pajaro como muerto por chocar con una tuberia.
     *
     * @param ocultarAlTocarSuelo si es true, el pajaro cae por gravedad hasta
     *   tocar el suelo y AHI se oculta (deja de renderizarse). Si es false,
     *   cae igualmente al suelo pero queda visible donde aterrizo.
     *
     *   Game lo usa asi: en modo 2 jugadores, cuando uno muere y el OTRO sigue
     *   vivo, se le pasa true para que despues de la caida desaparezca y no
     *   estorbe visualmente al que sigue jugando. Cuando muere el ultimo
     *   (terminando la partida), se le pasa false para que quede a la vista
     *   en el lugar del aterrizaje.
     */
    public void matar(boolean ocultarAlTocarSuelo) {
        vivo = false;
        this.ocultarAlTocarSuelo = ocultarAlTocarSuelo;
    }

    public void sumarPunto() {
        puntaje++;
    }

    /**
     * Animacion en estado "idle" (pantalla de inicio):
     * no aplica gravedad, solo hace flotar el ala suavemente.
     */
    public void animarIdle(float dt) {
        if (vivo) {
            timerAla += dt * 10.0f;
        }
    }

    // ============================================================
    // Consultas
    // ============================================================

    public float getX()       { return x; }
    public float getY()       { return y; }
    public float getVelY()    { return velY; }
    public boolean estaVivo() { return vivo; }
    public int getPuntaje()   { return puntaje; }

    public float getAncho() { return ANCHO_COLISION; }
    public float getAlto()  { return ALTO_COLISION; }

    // ============================================================
    // Render
    // ============================================================

    /**
     * Dibuja el pajaro como composicion de figuras:
     *   - Cola (triangulo apuntando a la izquierda)
     *   - Cuerpo (circulo)
     *   - Ala (triangulo animado debajo del cuerpo)
     *   - Pico (triangulo apuntando a la derecha)
     *   - Ojo (circulo blanco + pupila negra)
     *
     * La rotacion global del pajaro depende de la velocidad vertical:
     * sube -> nariz arriba, cae -> nariz abajo. Las partes se rotan en
     * conjunto: cada parte tiene un offset LOCAL respecto al centro del
     * pajaro, que se rota y traslada al mundo antes de dibujarse.
     */
    public void render(Renderer r) {
        // Si el pajaro fue marcado como oculto (murio y ya toco el suelo en
        // modo 2 jugadores con el otro vivo), no se dibuja nada.
        if (oculto) return;

        // Calculo la rotacion visual a partir de la velocidad (con limites).
        float rot = velY * 0.55f;
        if (rot >  0.50f) rot =  0.50f;
        if (rot < -0.80f) rot = -0.80f;
        // Si el pajaro esta muerto, lo dejo "tirado" mirando hacia abajo.
        if (!vivo) rot = -1.0f;

        float cosR = (float) Math.cos(rot);
        float sinR = (float) Math.sin(rot);

        // Con el orden ROT->ESC del shader, "ancho" es siempre la dimension
        // horizontal en pantalla y "alto" la vertical, sin importar la rotacion.

        // ---- Cola: triangulo apuntando a la izquierda ----
        // Offset local: detras del cuerpo, ligeramente arriba.
        dibujarParteTriangulo(r, -0.045f, 0.008f,
                              0.040f, 0.045f,
                              rot + (float) (Math.PI / 2.0),
                              cuerpoR * 0.7f, cuerpoG * 0.7f, cuerpoB * 0.7f,
                              cosR, sinR);

        // ---- Cuerpo: circulo principal ----
        r.dibujarCirculo(x, y, RADIO_CUERPO, cuerpoR, cuerpoG, cuerpoB);

        // ---- Ala: triangulo apuntando hacia abajo + oscilacion ----
        // El angulo base es PI (apunta abajo). Le sumo un seno para el aleteo.
        float oscilacionAla = (float) Math.sin(timerAla) * 0.55f;
        float anguloAla = (float) Math.PI + oscilacionAla;
        // Offset local del centro del ala (un poco hacia atras y abajo del cuerpo).
        dibujarParteTriangulo(r, -0.005f, -0.025f,
                              0.055f, 0.050f,
                              rot + anguloAla,
                              alaR, alaG, alaB,
                              cosR, sinR);

        // ---- Pico: triangulo apuntando a la derecha ----
        // El triangulo unitario apunta hacia arriba; con -PI/2 apunta a la derecha.
        dibujarParteTriangulo(r, 0.055f, 0.000f,
                              0.035f, 0.030f,
                              rot - (float) (Math.PI / 2.0),
                              1.00f, 0.55f, 0.10f,
                              cosR, sinR);

        // ---- Ojo: circulo blanco con pupila negra ----
        dibujarParteCirculo(r, 0.022f, 0.022f, RADIO_OJO,
                            1.0f, 1.0f, 1.0f, cosR, sinR);
        // La pupila va ligeramente "adelante" del ojo (hacia el pico).
        dibujarParteCirculo(r, 0.028f, 0.020f, RADIO_PUPILA,
                            0.05f, 0.05f, 0.10f, cosR, sinR);
    }

    /**
     * Helper: dibuja un triangulo cuyo CENTRO esta en (lx, ly) local del pajaro.
     * Aplica la rotacion global del pajaro al offset y suma la posicion del pajaro.
     */
    private void dibujarParteTriangulo(Renderer r, float lx, float ly,
                                       float ancho, float alto, float rotTotal,
                                       float cr, float cg, float cb,
                                       float cosR, float sinR) {
        float wx = x + (lx * cosR - ly * sinR);
        float wy = y + (lx * sinR + ly * cosR);
        r.dibujarTriangulo(wx, wy, ancho, alto, rotTotal, cr, cg, cb);
    }

    /**
     * Helper: dibuja un circulo cuyo CENTRO esta en (lx, ly) local del pajaro.
     */
    private void dibujarParteCirculo(Renderer r, float lx, float ly, float radio,
                                     float cr, float cg, float cb,
                                     float cosR, float sinR) {
        float wx = x + (lx * cosR - ly * sinR);
        float wy = y + (lx * sinR + ly * cosR);
        r.dibujarCirculo(wx, wy, radio, cr, cg, cb);
    }
}
