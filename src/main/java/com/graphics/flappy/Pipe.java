package com.graphics.flappy;

/**
 * Pipe:
 * Representa una tuberia con hueco vertical en mitad de la pantalla.
 *
 * Una tuberia consiste en dos rectangulos verticales (uno arriba y otro abajo)
 * separados por un "gap" centrado en gapCentroY. Cada tramo lleva ademas una
 * pequena "tapa" mas ancha en el extremo cercano al gap, al estilo del juego
 * clasico, para mejorar la estetica.
 *
 * El flag `puntuada` evita sumar dos veces el punto al pasar por la tuberia.
 */
public class Pipe {

    // Ancho del cuerpo principal de la tuberia.
    public static final float ANCHO = 0.18f;
    // Altura del hueco vertical entre tramo superior e inferior.
    public static final float GAP_ALTO = 0.48f;
    // Dimensiones de la "tapa" decorativa en cada extremo cercano al hueco.
    // Estas constantes las usa tanto el render como la colision para que la
    // hitbox coincida exactamente con lo dibujado.
    public static final float TAPA_ANCHO_EXTRA = 0.04f;
    public static final float TAPA_ALTO        = 0.04f;

    // Posicion horizontal del centro de la tuberia (se mueve a la izquierda).
    public float x;
    // Coordenada Y donde queda centrado el hueco.
    public final float gapCentroY;
    // True cuando ya se conto el punto por superar esta tuberia.
    public boolean puntuada;

    public Pipe(float x, float gapCentroY) {
        this.x = x;
        this.gapCentroY = gapCentroY;
        this.puntuada = false;
    }

    /**
     * Avanza la tuberia hacia la izquierda con la velocidad dada.
     */
    public void actualizar(float dt, float velocidad) {
        x -= velocidad * dt;
    }

    /**
     * @return true si la tuberia salio por la izquierda y se puede eliminar.
     */
    public boolean fueraDePantalla() {
        return x + (ANCHO * 0.5f) < -1.3f;
    }

    /**
     * Detecta colision AABB contra un pajaro.
     *
     * La tuberia se compone de 4 rectangulos de colision:
     *   - Cuerpo superior:  ancho ANCHO, de gapTop hasta el techo (+1).
     *   - Tapa superior:    ancho ANCHO + TAPA_ANCHO_EXTRA, alto TAPA_ALTO,
     *                       apoyada justo en gapTop.
     *   - Tapa inferior:    igual, apoyada en gapBottom.
     *   - Cuerpo inferior:  ancho ANCHO, de gapBottom hasta el piso (-1).
     *
     * Es importante incluir las tapas porque son mas anchas que el cuerpo
     * y, si solo se chequeara el cuerpo, el pajaro podria parecer "tocar"
     * la tapa sin perder.
     */
    public boolean colisionaCon(Bird bird) {
        float birdLeft   = bird.getX() - (bird.getAncho() * 0.5f);
        float birdRight  = bird.getX() + (bird.getAncho() * 0.5f);
        float birdBottom = bird.getY() - (bird.getAlto() * 0.5f);
        float birdTop    = bird.getY() + (bird.getAlto() * 0.5f);

        float pipeLeft  = x - (ANCHO * 0.5f);
        float pipeRight = x + (ANCHO * 0.5f);
        float tapaLeft  = x - (ANCHO + TAPA_ANCHO_EXTRA) * 0.5f;
        float tapaRight = x + (ANCHO + TAPA_ANCHO_EXTRA) * 0.5f;

        float gapTop    = gapCentroY + (GAP_ALTO * 0.5f);
        float gapBottom = gapCentroY - (GAP_ALTO * 0.5f);

        // 1) Cuerpos verticales (sin tapas), zona en X = cuerpo principal.
        boolean overlapCuerpo = birdRight > pipeLeft && birdLeft < pipeRight;
        if (overlapCuerpo && (birdTop > gapTop || birdBottom < gapBottom)) {
            return true;
        }

        // 2) Tapas: zona en X = cuerpo + extras a cada lado, zona en Y = solo
        //    la franja de la tapa. Estan apoyadas en gapTop (superior) y
        //    gapBottom (inferior).
        boolean overlapTapaX = birdRight > tapaLeft && birdLeft < tapaRight;
        if (overlapTapaX) {
            // Tapa superior cubre y ∈ [gapTop, gapTop + TAPA_ALTO].
            if (birdTop > gapTop && birdBottom < gapTop + TAPA_ALTO) {
                return true;
            }
            // Tapa inferior cubre y ∈ [gapBottom - TAPA_ALTO, gapBottom].
            if (birdBottom < gapBottom && birdTop > gapBottom - TAPA_ALTO) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renderiza la tuberia: cuerpo superior, cuerpo inferior y sus dos tapas.
     */
    public void render(Renderer r) {
        // Colores de la tuberia: cuerpo verde y borde mas oscuro.
        final float bodyR = 0.30f, bodyG = 0.78f, bodyB = 0.30f;
        final float capR  = 0.18f, capG  = 0.55f, capB  = 0.20f;

        float gapTop    = gapCentroY + (GAP_ALTO * 0.5f);
        float gapBottom = gapCentroY - (GAP_ALTO * 0.5f);

        // ---- Tramo superior ----
        float altoSuperior = 1.0f - gapTop;
        if (altoSuperior > 0.0f) {
            float yCentroSup = gapTop + (altoSuperior * 0.5f);
            r.dibujarRect(x, yCentroSup, ANCHO, altoSuperior, bodyR, bodyG, bodyB);
            // Tapa superior (sobre el borde del gap, mas ancha que el cuerpo).
            r.dibujarRect(x, gapTop + (TAPA_ALTO * 0.5f),
                          ANCHO + TAPA_ANCHO_EXTRA, TAPA_ALTO,
                          capR, capG, capB);
        }

        // ---- Tramo inferior ----
        float altoInferior = gapBottom + 1.0f;
        if (altoInferior > 0.0f) {
            float yCentroInf = -1.0f + (altoInferior * 0.5f);
            r.dibujarRect(x, yCentroInf, ANCHO, altoInferior, bodyR, bodyG, bodyB);
            // Tapa inferior (debajo del borde del gap).
            r.dibujarRect(x, gapBottom - (TAPA_ALTO * 0.5f),
                          ANCHO + TAPA_ANCHO_EXTRA, TAPA_ALTO,
                          capR, capG, capB);
        }
    }
}
