package com.graphics.flappy;

/**
 * Hud:
 * Encargado de dibujar la interfaz: puntajes, nivel y overlays para
 * los estados de inicio y game over.
 *
 * Como no usamos texturas ni fuentes externas, los numeros se dibujan
 * con un "display de 7 segmentos" hecho a base de pequenos rectangulos.
 * Cada digito se codifica con un entero que indica que segmentos estan
 * encendidos (un bit por segmento).
 *
 * Segmentos:
 *     A      <- arriba
 *   F   B    <- izq sup, der sup
 *     G      <- medio
 *   E   C    <- izq inf, der inf
 *     D      <- abajo
 *
 * Bit por segmento: A=1, B=2, C=4, D=8, E=16, F=32, G=64.
 */
public class Hud {

    // Mascara de segmentos para cada digito 0..9. Calculada a mano.
    private static final int[] DIGITOS = {
        /* 0 */ 1 + 2 + 4 + 8 + 16 + 32,      // A B C D E F
        /* 1 */ 2 + 4,                        // B C
        /* 2 */ 1 + 2 + 64 + 16 + 8,          // A B G E D
        /* 3 */ 1 + 2 + 64 + 4 + 8,           // A B G C D
        /* 4 */ 32 + 64 + 2 + 4,              // F G B C
        /* 5 */ 1 + 32 + 64 + 4 + 8,          // A F G C D
        /* 6 */ 1 + 32 + 64 + 16 + 4 + 8,     // A F G E C D
        /* 7 */ 1 + 2 + 4,                    // A B C
        /* 8 */ 1 + 2 + 4 + 8 + 16 + 32 + 64, // todos
        /* 9 */ 1 + 2 + 4 + 8 + 32 + 64       // A B C D F G
    };

    // Mascaras de bit por segmento (constantes para legibilidad).
    private static final int A = 1, B = 2, C = 4, D = 8, E = 16, F = 32, G = 64;

    // ============================================================
    // Colores de cada jugador (centralizados para reutilizar en overlays).
    // ============================================================
    private static final float[] COLOR_J1 = { 0.98f, 0.84f, 0.20f }; // amarillo
    private static final float[] COLOR_J2 = { 0.95f, 0.35f, 0.35f }; // rojo
    private static final float[] COLOR_J3 = { 0.30f, 0.65f, 0.95f }; // azul

    /**
     * Punto de entrada del HUD para el frame actual.
     *
     * @param jugadoresActivos 1, 2 o 3. Los inactivos no se renderizan.
     * @param ganador          0 si la partida no terminó por victoria;
     *                         1, 2 o 3 si algún jugador alcanzó META_PUNTOS.
     */
    public void render(Renderer r, Bird j1, Bird j2, Bird j3,
                       int jugadoresActivos, int nivel, int ganador,
                       Game.Estado estado) {
        // En SELECCION el overlay central tapa todo: no muestro marcadores.
        if (estado != Game.Estado.SELECCION) {
            renderizarPanelesMarcador(r, j1, j2, j3, jugadoresActivos);
            renderizarPanelNivel(r, nivel);
        }

        // Overlays especiales por estado.
        switch (estado) {
            case SELECCION -> renderizarOverlaySeleccion(r);
            case INICIO    -> renderizarOverlayInicio(r, jugadoresActivos);
            case GAMEOVER  -> renderizarOverlayGameOver(r, j1, j2, j3, jugadoresActivos);
            case VICTORIA  -> renderizarOverlayVictoria(r, j1, j2, j3, ganador);
            default -> {} // JUGANDO: sin overlay
        }
    }

    /**
     * Dibuja los paneles superiores de puntaje. Layout:
     *   - J1 arriba izquierda
     *   - J2 arriba derecha
     *   - J3 arriba centro-superior (encima del panel de nivel)
     * Los paneles inactivos no se dibujan.
     */
    private void renderizarPanelesMarcador(Renderer r, Bird j1, Bird j2, Bird j3,
                                           int jugadoresActivos) {
        final float anchoD   = 0.040f;
        final float altoD    = 0.085f;
        final float espesorD = 0.007f;

        // Panel J1 (esquina sup izquierda).
        dibujarPanelJugador(r, j1, -0.78f, 0.91f, 1, -0.95f, -0.62f,
                            COLOR_J1, anchoD, altoD, espesorD, -0.97f);

        // Panel J2 (esquina sup derecha): solo en modo >= 2.
        if (jugadoresActivos >= 2) {
            dibujarPanelJugador(r, j2, 0.78f, 0.91f, 2, 0.95f, 0.73f,
                                COLOR_J2, anchoD, altoD, espesorD, 0.97f);
        }

        // Panel J3: arriba en el centro (un poco mas chico para no chocar con
        // el panel de nivel). Solo en modo 3.
        if (jugadoresActivos >= 3) {
            // Como va al centro y arriba, lo dejo justo encima del panel de nivel.
            // Centro Y = 0.80 (mas abajo que J1/J2 que estan en 0.91).
            float panelY = 0.78f;
            r.dibujarRect(0.0f, panelY, 0.34f, 0.10f, 0.18f, 0.20f, 0.28f);
            dibujarDigito(r, 3, -0.14f, panelY, 0.04f, 0.07f, 0.005f,
                          COLOR_J3[0], COLOR_J3[1], COLOR_J3[2]);
            dibujarNumero(r, j3.getPuntaje(), 0.13f, panelY, anchoD, altoD * 0.85f, espesorD,
                          COLOR_J3[0], COLOR_J3[1], COLOR_J3[2]);
            // Indicador vivo/muerto del J3 a la derecha del panel.
            r.dibujarCirculo(0.18f, panelY + 0.10f, 0.018f,
                             j3.estaVivo() ? 0.30f : 0.55f,
                             j3.estaVivo() ? 0.85f : 0.20f,
                             j3.estaVivo() ? 0.30f : 0.20f);
        }
    }

    /**
     * Helper para dibujar un panel de jugador (J1 o J2), incluyendo:
     * fondo, indicador de número del jugador, puntaje y luz viva/muerta.
     */
    private void dibujarPanelJugador(Renderer r, Bird b, float panelX, float panelY,
                                     int numJugador, float xIndicador, float xDerechaPuntaje,
                                     float[] color, float anchoD, float altoD, float espesorD,
                                     float xLuz) {
        r.dibujarRect(panelX, panelY, 0.38f, 0.13f, 0.18f, 0.20f, 0.28f);
        dibujarDigito(r, numJugador, xIndicador, panelY, 0.04f, 0.08f, 0.005f,
                      color[0], color[1], color[2]);
        dibujarNumero(r, b.getPuntaje(), xDerechaPuntaje, panelY, anchoD, altoD, espesorD,
                      color[0], color[1], color[2]);
        r.dibujarCirculo(xLuz, 0.78f, 0.020f,
                         b.estaVivo() ? 0.30f : 0.55f,
                         b.estaVivo() ? 0.85f : 0.20f,
                         b.estaVivo() ? 0.30f : 0.20f);
    }

    /**
     * Panel central superior con el indicador de nivel.
     */
    private void renderizarPanelNivel(Renderer r, int nivel) {
        r.dibujarRect(0.0f, 0.92f, 0.22f, 0.10f, 0.18f, 0.20f, 0.28f);
        dibujarNumero(r, nivel + 1, 0.04f, 0.92f, 0.045f, 0.085f, 0.007f,
                      1.0f, 1.0f, 1.0f);
        // Triangulo decorativo a la izquierda del numero ("nivel").
        r.dibujarTriangulo(-0.07f, 0.92f, 0.05f, 0.06f, 0.0f,
                           1.0f, 0.85f, 0.30f);
    }

    /**
     * Pantalla previa: el jugador elige entre 1, 2 o 3 jugadores.
     * Muestra tres "tarjetas" alineadas, cada una con su digito y los
     * pajaros que jugaran en ese modo.
     */
    private void renderizarOverlaySeleccion(Renderer r) {
        // Fondo del panel central, ocupando casi toda la pantalla.
        r.dibujarRect(0.0f, 0.0f, 1.85f, 0.85f, 0.10f, 0.12f, 0.18f);
        r.dibujarRect(0.0f, 0.0f, 1.81f, 0.81f, 0.92f, 0.92f, 0.92f);

        // Banda superior decorativa.
        r.dibujarRect(0.0f, 0.32f, 1.81f, 0.10f, 0.20f, 0.65f, 0.30f);

        // Tres tarjetas: 1 jugador (izq), 2 jugadores (centro), 3 jugadores (der).
        dibujarTarjeta(r, -0.60f, 1, new float[][]{ COLOR_J1 });
        dibujarTarjeta(r,  0.00f, 2, new float[][]{ COLOR_J1, COLOR_J2 });
        dibujarTarjeta(r,  0.60f, 3, new float[][]{ COLOR_J1, COLOR_J2, COLOR_J3 });
    }

    /**
     * Dibuja una tarjeta de seleccion: un cuadrado con un digito grande y
     * tantos circulitos como jugadores haya en ese modo.
     */
    private void dibujarTarjeta(Renderer r, float cx, int numero, float[][] colores) {
        r.dibujarRect(cx, -0.05f, 0.50f, 0.50f, 0.85f, 0.85f, 0.88f);
        r.dibujarRect(cx, -0.05f, 0.46f, 0.46f, 0.98f, 0.98f, 1.00f);
        dibujarDigito(r, numero, cx, 0.05f, 0.10f, 0.18f, 0.015f,
                      0.18f, 0.20f, 0.28f);
        // Pajaros iconicos (uno por color). Reparto el ancho dependiendo de cuantos hay.
        float radio = colores.length == 1 ? 0.05f : 0.04f;
        float ancho = (colores.length - 1) * 0.10f;
        for (int i = 0; i < colores.length; i++) {
            float x = cx - ancho * 0.5f + i * 0.10f;
            r.dibujarCirculo(x, -0.17f, radio,
                             colores[i][0], colores[i][1], colores[i][2]);
        }
    }

    /**
     * Overlay sobre la pantalla de inicio: panel a la derecha con indicaciones
     * de control. Se posiciona a la derecha para no tapar a los pajaros, que
     * estan dibujados en x = -0.45.
     *
     * En modo 1 jugador solo se muestra la fila del J1; el panel se achica
     * para no tener una segunda fila vacia.
     */
    private void renderizarOverlayInicio(Renderer r, int jugadoresActivos) {
        // Panel desplazado a la derecha. La altura crece con la cantidad de
        // jugadores (cada fila ocupa ~0.20 NDC).
        final float cx = 0.35f, cy = 0.0f;
        float alto = switch (jugadoresActivos) {
            case 1 -> 0.40f;
            case 2 -> 0.60f;
            case 3 -> 0.75f;
            default -> 0.40f;
        };
        r.dibujarRect(cx, cy, 1.10f, alto,         0.10f, 0.12f, 0.18f);
        r.dibujarRect(cx, cy, 1.06f, alto - 0.04f, 0.92f, 0.92f, 0.92f);

        // Filas: cada jugador en su propia fila, centradas verticalmente.
        // Calculo el Y de la primera fila en funcion de la cantidad.
        float separacion = 0.22f;
        float yFila0 = (jugadoresActivos - 1) * (separacion * 0.5f);
        dibujarFilaInicio(r, cx, yFila0, 1, COLOR_J1);
        if (jugadoresActivos >= 2) {
            dibujarFilaInicio(r, cx, yFila0 - separacion, 2, COLOR_J2);
        }
        if (jugadoresActivos >= 3) {
            dibujarFilaInicio(r, cx, yFila0 - 2 * separacion, 3, COLOR_J3);
        }

        // Triangulo "play" a la derecha del panel.
        r.dibujarTriangulo(cx + 0.40f, 0.0f, 0.16f, 0.14f,
                           -(float) (Math.PI / 2.0),
                           0.20f, 0.65f, 0.30f);
    }

    /**
     * Una fila del overlay de inicio: punto de color + cuadradito con un
     * digito que simboliza la tecla del jugador.
     */
    private void dibujarFilaInicio(Renderer r, float cx, float y, int numero, float[] color) {
        r.dibujarCirculo(cx - 0.42f, y, 0.05f, color[0], color[1], color[2]);
        r.dibujarRect(cx - 0.10f, y, 0.45f, 0.10f, 0.20f, 0.20f, 0.25f);
        dibujarDigito(r, numero, cx - 0.10f, y, 0.04f, 0.07f, 0.005f,
                      1.0f, 1.0f, 1.0f);
    }

    /**
     * Overlay de game over: panel central con los puntajes finales.
     * Layout dinamico segun cuantos jugadores activos hay.
     */
    private void renderizarOverlayGameOver(Renderer r, Bird j1, Bird j2, Bird j3,
                                            int jugadoresActivos) {
        // Fondo del panel (mas ancho para 3 jugadores).
        float anchoPanel = jugadoresActivos == 3 ? 1.40f : 1.20f;
        r.dibujarRect(0.0f, 0.0f, anchoPanel, 0.65f, 0.10f, 0.12f, 0.18f);
        r.dibujarRect(0.0f, 0.0f, anchoPanel - 0.04f, 0.61f, 0.92f, 0.92f, 0.92f);

        // Banda superior roja "Game Over".
        r.dibujarRect(0.0f, 0.22f, anchoPanel - 0.04f, 0.10f, 0.78f, 0.22f, 0.22f);

        // Tamanio y separacion segun cuantos hay que mostrar.
        if (jugadoresActivos == 1) {
            // Centrado, grande.
            dibujarBloquePuntaje(r, -0.15f, 0.02f, 0.06f, 0.10f, j1.getPuntaje(), COLOR_J1, 0.09f, 0.18f, 0.014f);
        } else if (jugadoresActivos == 2) {
            dibujarBloquePuntaje(r, -0.36f, 0.02f, 0.05f, 0.16f, j1.getPuntaje(), COLOR_J1, 0.07f, 0.14f, 0.012f);
            dibujarBloquePuntaje(r,  0.10f, 0.02f, 0.05f, 0.20f, j2.getPuntaje(), COLOR_J2, 0.07f, 0.14f, 0.012f);
        } else {
            // 3 jugadores: tres bloques mas chicos repartidos.
            dibujarBloquePuntaje(r, -0.48f, 0.02f, 0.045f, 0.12f, j1.getPuntaje(), COLOR_J1, 0.055f, 0.11f, 0.010f);
            dibujarBloquePuntaje(r, -0.05f, 0.02f, 0.045f, 0.12f, j2.getPuntaje(), COLOR_J2, 0.055f, 0.11f, 0.010f);
            dibujarBloquePuntaje(r,  0.38f, 0.02f, 0.045f, 0.12f, j3.getPuntaje(), COLOR_J3, 0.055f, 0.11f, 0.010f);
        }

        // Triangulo "reintentar" abajo + signo de "menu" (cuadrado) a su lado.
        r.dibujarTriangulo(-0.10f, -0.22f, 0.12f, 0.14f, 0.0f,
                           0.20f, 0.65f, 0.30f);
        r.dibujarRect(0.10f, -0.22f, 0.12f, 0.10f, 0.50f, 0.55f, 0.65f);
    }

    /**
     * Overlay de VICTORIA: muestra al jugador ganador en grande con su color
     * y el puntaje que alcanzo.
     */
    private void renderizarOverlayVictoria(Renderer r, Bird j1, Bird j2, Bird j3,
                                           int ganador) {
        if (ganador < 1 || ganador > 3) return;

        Bird ganBird = switch (ganador) {
            case 1 -> j1;
            case 2 -> j2;
            case 3 -> j3;
            default -> j1;
        };
        float[] color = switch (ganador) {
            case 1 -> COLOR_J1;
            case 2 -> COLOR_J2;
            case 3 -> COLOR_J3;
            default -> COLOR_J1;
        };

        // Fondo del panel.
        r.dibujarRect(0.0f, 0.0f, 1.30f, 0.75f, 0.10f, 0.12f, 0.18f);
        r.dibujarRect(0.0f, 0.0f, 1.26f, 0.71f, 0.97f, 0.97f, 0.95f);

        // Banda superior con el color del ganador como celebracion.
        r.dibujarRect(0.0f, 0.27f, 1.26f, 0.12f, color[0], color[1], color[2]);

        // Pajaro grande del ganador en el centro-izquierdo.
        r.dibujarCirculo(-0.35f, 0.02f, 0.09f, color[0], color[1], color[2]);

        // Numero gigante del jugador ganador a la derecha del pajaro.
        dibujarDigito(r, ganador, 0.05f, 0.02f, 0.12f, 0.22f, 0.018f,
                      0.18f, 0.20f, 0.28f);

        // Puntaje final con el que gano (debajo del numero).
        dibujarNumero(r, ganBird.getPuntaje(), 0.40f, 0.02f, 0.08f, 0.16f, 0.013f,
                      color[0], color[1], color[2]);

        // Iconos abajo: triangulo (repetir) + cuadrado (menu).
        r.dibujarTriangulo(-0.10f, -0.26f, 0.12f, 0.14f, 0.0f,
                           0.20f, 0.65f, 0.30f);
        r.dibujarRect(0.10f, -0.26f, 0.12f, 0.10f, 0.50f, 0.55f, 0.65f);
    }

    /**
     * Helper: bloque "circulo de color + puntaje" para los overlays de
     * gameover y victoria. xDot es el centro del circulo del jugador y
     * xNum es donde arrancan los digitos del puntaje (alineado a derecha).
     */
    private void dibujarBloquePuntaje(Renderer r, float xDot, float y,
                                       float radio, float xNum, int puntaje,
                                       float[] color, float anchoD, float altoD,
                                       float espesorD) {
        r.dibujarCirculo(xDot, y, radio, color[0], color[1], color[2]);
        dibujarNumero(r, puntaje, xNum, y, anchoD, altoD, espesorD,
                      0.18f, 0.20f, 0.28f);
    }

    // ============================================================
    // Dibujo de digitos
    // ============================================================

    /**
     * Dibuja un numero entero (positivo) con sus digitos uno detras del otro
     * a partir de la posicion (xDerecha, y) (el origen es el extremo derecho
     * para que numeros de distinta cantidad de digitos queden alineados a la
     * derecha en el panel).
     */
    private void dibujarNumero(Renderer r, int valor,
                               float xDerecha, float y,
                               float ancho, float alto, float espesor,
                               float cr, float cg, float cb) {
        if (valor < 0) valor = 0;
        // Espaciado horizontal entre digitos.
        float paso = ancho + ancho * 0.4f;
        if (valor == 0) {
            dibujarDigito(r, 0, xDerecha, y, ancho, alto, espesor, cr, cg, cb);
            return;
        }
        float x = xDerecha;
        while (valor > 0) {
            int d = valor % 10;
            dibujarDigito(r, d, x, y, ancho, alto, espesor, cr, cg, cb);
            x -= paso;
            valor /= 10;
        }
    }

    /**
     * Dibuja un digito (0..9) como display de 7 segmentos centrado en (x, y).
     * @param ancho   ancho total del digito.
     * @param alto    alto total del digito.
     * @param espesor grosor de cada segmento.
     */
    private void dibujarDigito(Renderer r, int digito,
                               float x, float y,
                               float ancho, float alto, float espesor,
                               float cr, float cg, float cb) {
        if (digito < 0 || digito > 9) return;
        int mascara = DIGITOS[digito];

        // Largo de los segmentos horizontales y verticales.
        float largoH = ancho - 2.0f * espesor;
        float largoV = alto * 0.5f - 2.0f * espesor;

        // Posiciones de cada segmento (centro en coords locales del digito).
        if ((mascara & A) != 0) // arriba
            r.dibujarRect(x, y + alto * 0.5f - espesor * 0.5f, largoH, espesor, cr, cg, cb);
        if ((mascara & D) != 0) // abajo
            r.dibujarRect(x, y - alto * 0.5f + espesor * 0.5f, largoH, espesor, cr, cg, cb);
        if ((mascara & G) != 0) // medio
            r.dibujarRect(x, y, largoH, espesor, cr, cg, cb);
        if ((mascara & F) != 0) // izq sup
            r.dibujarRect(x - ancho * 0.5f + espesor * 0.5f,
                          y + alto * 0.25f, espesor, largoV, cr, cg, cb);
        if ((mascara & E) != 0) // izq inf
            r.dibujarRect(x - ancho * 0.5f + espesor * 0.5f,
                          y - alto * 0.25f, espesor, largoV, cr, cg, cb);
        if ((mascara & B) != 0) // der sup
            r.dibujarRect(x + ancho * 0.5f - espesor * 0.5f,
                          y + alto * 0.25f, espesor, largoV, cr, cg, cb);
        if ((mascara & C) != 0) // der inf
            r.dibujarRect(x + ancho * 0.5f - espesor * 0.5f,
                          y - alto * 0.25f, espesor, largoV, cr, cg, cb);
    }
}
