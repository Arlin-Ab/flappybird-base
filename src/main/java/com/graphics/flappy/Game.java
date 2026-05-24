package com.graphics.flappy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * Game:
 * Orquesta el bucle de juego, el estado global, los pajaros, las tuberias,
 * la dificultad progresiva, las pantallas (inicio / jugando / game over) y
 * el HUD.
 *
 * No conoce los detalles de bajo nivel de OpenGL (eso lo maneja Renderer).
 *
 * Estados del juego:
 *   - INICIO:    se muestra fondo y ambos pajaros flotando; espera input.
 *   - JUGANDO:   la simulacion avanza; los jugadores controlan sus pajaros.
 *   - GAMEOVER:  ambos pajaros estan muertos; SPACE o R reinicia.
 *
 * Modo 2 jugadores:
 *   - Jugador 1 (amarillo)  -> SPACE
 *   - Jugador 2 (rojo)      -> W o flecha ARRIBA
 *   El game over ocurre solo cuando AMBOS pajaros mueren; mientras uno
 *   siga vivo, el otro espera. Comparten un mismo set de tuberias.
 */
public class Game {

    // ============================================================
    // Configuracion
    // ============================================================

    // X compartida por los dos pajaros (mismo plano de competencia).
    private static final float BIRD_X = -0.45f;

    // Parametros base de tuberias (se ajustan con la dificultad).
    private static final float TIEMPO_ENTRE_TUBERIAS_BASE = 1.50f;
    private static final float VELOCIDAD_TUBERIAS_BASE    = 0.62f;
    private static final float TIEMPO_ENTRE_TUBERIAS_MIN  = 0.90f;
    private static final float VELOCIDAD_TUBERIAS_MAX     = 1.40f;
    // Rango vertical del centro del hueco al generar una tuberia.
    private static final float GAP_MIN_CENTRO = -0.45f;
    private static final float GAP_MAX_CENTRO = 0.45f;

    // Espesor de la franja de pasto encima de la tierra.
    // El tope visual del pasto se toma directamente de Bird.Y_SUELO, asi el
    // pajaro muere justo cuando toca lo que se ve, sin "entrar" en el suelo.
    private static final float PASTO_ALTO = 0.05f;

    // ============================================================
    // Estado
    // ============================================================

    // Estados del juego:
    //   SELECCION: pantalla previa donde se elige 1, 2 o 3 jugadores.
    //   INICIO:    los pajaros estan en pantalla, esperando primer salto.
    //   JUGANDO:   la simulacion corre normalmente.
    //   GAMEOVER:  los pajaros activos murieron; espera reinicio.
    //   VICTORIA:  algun jugador alcanzo META_PUNTOS; muestra al ganador.
    public enum Estado { SELECCION, INICIO, JUGANDO, GAMEOVER, VICTORIA }

    // ----------------------------------------------------------------
    // META de la partida
    // ----------------------------------------------------------------
    // Cantidad de puntos que un jugador debe alcanzar para ganar la partida.
    // Cuando cualquier jugador llega a este puntaje, la partida termina con
    // estado VICTORIA y se anuncia al ganador en pantalla. Cambialo aqui para
    // hacer partidas mas cortas o mas largas.


    public static final int META_PUNTOS = 2;

    private final long window;
    private final Renderer renderer;
    private final InputManager input;
    private final Hud hud;
    private final SoundManager sonido;
    private final Random random = new Random();

    private final Bird jugador1;
    private final Bird jugador2;
    private final Bird jugador3;
    private final List<Pipe> tuberias = new ArrayList<>();
    // Nubes decorativas que se mueven lentamente para dar parallax.
    private final List<float[]> nubes = new ArrayList<>(); // cada nube: {x, y, radio}

    private Estado estado;
    private float timerSpawn;

    // Cantidad de jugadores activos en la partida actual (1, 2). Se elige
    // en la pantalla SELECCION. Los pajaros que no esten activos se ignoran
    // por completo (no se actualizan, ni se renderizan, ni colisionan).
    private int jugadoresActivos;

    // Numero del jugador ganador (1, 2), o 0 si no hay ganador. Se setea
    // cuando algun jugador alcanza META_PUNTOS. Usado por el HUD para mostrar
    // la pantalla de VICTORIA con el color del ganador.
    private int ganador;

    // Dificultad calculada cada frame en funcion del puntaje maximo.
    private float velocidadTuberiasActual;
    private float intervaloTuberiasActual;
    private int nivelActual;

    // ============================================================
    // Construccion
    // ============================================================

    public Game(long window, Renderer renderer, InputManager input, SoundManager sonido) {
        this.window = window;
        this.renderer = renderer;
        this.input = input;
        this.hud = new Hud();
        this.sonido = sonido;

        // Jugador 1: amarillo brillante, ala mas oscura.
        this.jugador1 = new Bird(BIRD_X,
                                 0.98f, 0.84f, 0.20f,
                                 0.85f, 0.55f, 0.10f);
        // Jugador 2: rojo-coral, ala mas oscura.
        this.jugador2 = new Bird(BIRD_X,
                                 0.95f, 0.35f, 0.35f,
                                 0.70f, 0.18f, 0.18f);
        // Jugador 3:azul
        
        this.jugador3 = new Bird(BIRD_X,
                                 0.30f, 0.65f, 0.95f,
                                 0.10f, 0.40f, 0.70f);

        // Modo por defecto antes de que el usuario elija (no se usa hasta SELECCION).
        this.jugadoresActivos = 2;
        this.ganador = 0;

        inicializarNubes();
        reset();
    }

    /**
     * Posiciones iniciales y aleatorias para las nubes decorativas.
     */
    private void inicializarNubes() {
        nubes.clear();
        for (int i = 0; i < 5; i++) {
            float x = -1.1f + i * 0.5f + random.nextFloat() * 0.2f;
            float y = 0.35f + random.nextFloat() * 0.55f;
            float radio = 0.06f + random.nextFloat() * 0.05f;
            nubes.add(new float[]{ x, y, radio });
        }
    }

    /**
     * Reinicia todo y vuelve al menu de seleccion (1 o 2 jugadores).
     * Se llama al arrancar el juego y al pedir "menu" desde game over.
     */
    public void reset() {
        // Posiciones Y iniciales separadas para que los pajaros no se solapen
        // visualmente
        jugador1.reset( 0.25f);
        jugador2.reset( 0.00f);
        jugador3.reset(-0.25f);

        tuberias.clear();
        timerSpawn = 0.0f;
        ganador = 0;
        estado = Estado.SELECCION;
        actualizarDificultad();
        actualizarTitulo();
    }

    /**
     * Reinicio rapido: reinicia los pajaros, tuberias y dificultad pero
     * MANTIENE el modo (jugadoresActivos) y va directo a INICIO sin pasar
     * por la pantalla de seleccion. Util cuando el jugador quiere repetir
     * la misma partida sin volver a elegir modo.
     */
    public void resetMismoModo() {
        jugador1.reset( 0.25f);
        jugador2.reset( 0.00f);
        jugador3.reset(-0.25f);
        tuberias.clear();
        timerSpawn = 0.0f;
        ganador = 0;
        estado = Estado.INICIO;
        actualizarDificultad();
        actualizarTitulo();
    }

    // ============================================================
    // Bucle por frame
    // ============================================================

    /**
     * Procesa input segun el estado actual del juego.
     *
     * Flujo de teclas por estado:
     *   SELECCION: 1 -> un jugador, 2 -> dos jugadores, 3 -> tres jugadores.
     *   INICIO:    SPACE (J1), W (J2), UP (J3) -> arranca y salta.
     *   JUGANDO:   los mismos controles hacen saltar al pajaro vivo.
     *   GAMEOVER / VICTORIA:  SPACE/R repiten; ENTER vuelve al menu.
     *   En todo momento: ESC cierra la ventana.
     */
    public void procesarInput() {
        // Cerrar ventana con ESC en cualquier estado.
        if (input.justPresionada(GLFW.GLFW_KEY_ESCAPE)) {
            GLFW.glfwSetWindowShouldClose(window, true);
        }

        switch (estado) {
            case SELECCION -> {
                if (input.justPresionada(GLFW.GLFW_KEY_1)) {
                    jugadoresActivos = 1;
                    estado = Estado.INICIO;
                    actualizarTitulo();
                } else if (input.justPresionada(GLFW.GLFW_KEY_2)) {
                    jugadoresActivos = 2;
                    estado = Estado.INICIO;
                    actualizarTitulo();
                } else if (input.justPresionada(GLFW.GLFW_KEY_3)) {
                    jugadoresActivos = 3;
                    estado = Estado.INICIO;
                    actualizarTitulo();
                }
            }
            case INICIO -> {
                // Cualquier salto inicia el juego.
                boolean p1 = input.justPresionada(GLFW.GLFW_KEY_SPACE);
                boolean p2 = jugadoresActivos >= 2
                          && input.justPresionada(GLFW.GLFW_KEY_W);
                boolean p3 = jugadoresActivos >= 3
                          && input.justPresionada(GLFW.GLFW_KEY_UP);
                if (p1 || p2 || p3) {
                    estado = Estado.JUGANDO;
                    if (p1) { jugador1.saltar(); sonido.playSalto(); }
                    if (p2) { jugador2.saltar(); sonido.playSalto(); }
                    if (p3) { jugador3.saltar(); sonido.playSalto(); }
                    actualizarTitulo();
                }
            }
            case JUGANDO -> {
                if (input.justPresionada(GLFW.GLFW_KEY_SPACE)) {
                    jugador1.saltar();
                    if (jugador1.estaVivo()) sonido.playSalto();
                }
                if (jugadoresActivos >= 2
                 && input.justPresionada(GLFW.GLFW_KEY_W)) {
                    jugador2.saltar();
                    if (jugador2.estaVivo()) sonido.playSalto();
                }
                if (jugadoresActivos >= 3
                 && input.justPresionada(GLFW.GLFW_KEY_UP)) {
                    jugador3.saltar();
                    if (jugador3.estaVivo()) sonido.playSalto();
                }
            }
            case GAMEOVER, VICTORIA -> {
                if (input.justPresionada(GLFW.GLFW_KEY_R)
                 || input.justPresionada(GLFW.GLFW_KEY_SPACE)) {
                    // Reintento rapido: mismo modo, sin pasar por el menu.
                    resetMismoModo();
                } else if (input.justPresionada(GLFW.GLFW_KEY_ENTER)) {
                    // Volver al menu de seleccion para cambiar de modo.
                    reset();
                }
            }
        }
    }

    /**
     * Avanza la simulacion un frame.
     */
    public void actualizar(float dt) {
        if (estado != Estado.JUGANDO) {
            // En SELECCION/INICIO/GAMEOVER/VICTORIA solo dejo correr las nubes
            // para que la escena se vea viva. En INICIO ademas animo el aleteo
            // de los pajaros activos.
            moverNubes(dt, 0.04f);
            if (estado == Estado.INICIO) {
                jugador1.animarIdle(dt);
                if (jugadoresActivos >= 2) jugador2.animarIdle(dt);
                if (jugadoresActivos >= 3) jugador3.animarIdle(dt);
            }
            return;
        }

        // 1) Ajustar dificultad segun el puntaje maximo entre los jugadores activos.
        actualizarDificultad();

        // 2) Fisica de los pajaros activos. Los inactivos se ignoran.
        jugador1.actualizar(dt);
        if (jugadoresActivos >= 2) jugador2.actualizar(dt);
        if (jugadoresActivos >= 3) jugador3.actualizar(dt);

        // 3) Spawn de nuevas tuberias.
        timerSpawn += dt;
        if (timerSpawn >= intervaloTuberiasActual) {
            timerSpawn = 0.0f;
            spawnTuberia();
        }

        // 4) Mover tuberias y procesar puntaje/colision para cada jugador activo.
        Iterator<Pipe> it = tuberias.iterator();
        while (it.hasNext()) {
            Pipe t = it.next();
            t.actualizar(dt, velocidadTuberiasActual);

            // Cuando la tuberia ya quedo atras del pajaro, suma punto a cada
            // jugador vivo que la haya pasado.
            if (!t.puntuada && t.x + (Pipe.ANCHO * 0.5f) < BIRD_X) {
                t.puntuada = true;
                boolean alguienSumo = false;
                if (jugador1.estaVivo()) { jugador1.sumarPunto(); alguienSumo = true; }
                if (jugadoresActivos >= 2 && jugador2.estaVivo()) {
                    jugador2.sumarPunto(); alguienSumo = true;
                }
                if (jugadoresActivos >= 3 && jugador3.estaVivo()) {
                    jugador3.sumarPunto(); alguienSumo = true;
                }
                if (alguienSumo) sonido.playPunto();

                // Chequear si algun jugador llego a la META para terminar la
                // partida con VICTORIA.
                int ganadorPotencial = chequearGanador();
                if (ganadorPotencial != 0) {
                    ganador = ganadorPotencial;
                    estado = Estado.VICTORIA;
                    sonido.playPunto();
                    actualizarTitulo();
                    return; // No proceso mas colisiones este frame.
                }
                actualizarTitulo();
            }

            // Colision tuberia <-> pajaro (cada uno solo si esta vivo).
            // El que muere desaparece al tocar el suelo si HAY otro jugador
            // vivo (asi no estorba al que sigue). Si era el ultimo vivo, queda
            // a la vista para mostrar donde murio.
            if (jugador1.estaVivo() && t.colisionaCon(jugador1)) {
                jugador1.matar(quedanOtrosVivos(1));
            }
            if (jugadoresActivos >= 2 && jugador2.estaVivo() && t.colisionaCon(jugador2)) {
                jugador2.matar(quedanOtrosVivos(2));
            }
            if (jugadoresActivos >= 3 && jugador3.estaVivo() && t.colisionaCon(jugador3)) {
                jugador3.matar(quedanOtrosVivos(3));
            }

            if (t.fueraDePantalla()) it.remove();
        }

        // 5) Fin de partida por muerte: cuando TODOS los activos estan muertos.
        if (todosMuertos()) {
            estado = Estado.GAMEOVER;
            sonido.playGameOver();
            actualizarTitulo();
        }

        // 6) Nubes de fondo (mas rapidas en niveles altos para sensacion de velocidad).
        moverNubes(dt, 0.04f + nivelActual * 0.01f);
    }

    /**
     * Genera una nueva tuberia en el borde derecho con altura del hueco aleatoria.
     */
    private void spawnTuberia() {
        float gapCentro = GAP_MIN_CENTRO + random.nextFloat() * (GAP_MAX_CENTRO - GAP_MIN_CENTRO);
        tuberias.add(new Pipe(1.2f, gapCentro));
    }

    /**
     * Calcula el nivel actual y de ahi los parametros de dificultad.
     * El nivel sube cada 5 puntos del jugador con mas puntaje, considerando
     * todos los jugadores activos.
     */
    private void actualizarDificultad() {
        int puntosMax = jugador1.getPuntaje();
        if (jugadoresActivos >= 2) puntosMax = Math.max(puntosMax, jugador2.getPuntaje());
        if (jugadoresActivos >= 3) puntosMax = Math.max(puntosMax, jugador3.getPuntaje());
        nivelActual = puntosMax / 5;

        velocidadTuberiasActual = VELOCIDAD_TUBERIAS_BASE + nivelActual * 0.10f;
        if (velocidadTuberiasActual > VELOCIDAD_TUBERIAS_MAX) {
            velocidadTuberiasActual = VELOCIDAD_TUBERIAS_MAX;
        }
        intervaloTuberiasActual = TIEMPO_ENTRE_TUBERIAS_BASE - nivelActual * 0.07f;
        if (intervaloTuberiasActual < TIEMPO_ENTRE_TUBERIAS_MIN) {
            intervaloTuberiasActual = TIEMPO_ENTRE_TUBERIAS_MIN;
        }
    }

    /**
     * Revisa si algun jugador activo alcanzo META_PUNTOS. Si dos llegan en el
     * mismo frame (raro porque cada uno suma de a uno) devuelve el de menor
     * numero (J1 > J2 > J3).
     *
     * @return numero del jugador ganador (1, 2 o 3), o 0 si nadie llego.
     */
    private int chequearGanador() {
        if (jugador1.getPuntaje() >= META_PUNTOS) return 1;
        if (jugadoresActivos >= 2 && jugador2.getPuntaje() >= META_PUNTOS) return 2;
        if (jugadoresActivos >= 3 && jugador3.getPuntaje() >= META_PUNTOS) return 3;
        return 0;
    }

    /**
     * Devuelve true si, excluyendo al pajaro "queMurio" (1, 2 o 3), todavia
     * queda al menos otro jugador activo vivo. Se usa al matar para decidir
     * si el muerto debe desaparecer (otro sigue jugando) o quedar visible
     * (era el ultimo, fin de partida).
     */
    private boolean quedanOtrosVivos(int queMurio) {
        if (queMurio != 1 && jugador1.estaVivo()) return true;
        if (queMurio != 2 && jugadoresActivos >= 2 && jugador2.estaVivo()) return true;
        if (queMurio != 3 && jugadoresActivos >= 3 && jugador3.estaVivo()) return true;
        return false;
    }

    /** True si todos los pajaros activos estan muertos. */
    private boolean todosMuertos() {
        if (jugador1.estaVivo()) return false;
        if (jugadoresActivos >= 2 && jugador2.estaVivo()) return false;
        if (jugadoresActivos >= 3 && jugador3.estaVivo()) return false;
        return true;
    }

    /**
     * Avanza las nubes a la izquierda con una velocidad relativamente lenta
     * (efecto parallax). Cuando una nube sale, vuelve a aparecer por la derecha.
     */
    private void moverNubes(float dt, float velocidad) {
        for (float[] n : nubes) {
            n[0] -= velocidad * dt;
            if (n[0] + n[2] < -1.2f) {
                n[0] = 1.1f + random.nextFloat() * 0.4f;
                n[1] = 0.35f + random.nextFloat() * 0.55f;
                n[2] = 0.06f + random.nextFloat() * 0.05f;
            }
        }
    }

    // ============================================================
    // Render
    // ============================================================

    /**
     * Renderiza el frame completo.
     */
    public void render() {
        // Limpio el framebuffer con un azul de fondo (es la base que tapara el
        // gradiente del cielo).
        GL11.glClearColor(0.45f, 0.75f, 0.95f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        renderer.begin();

        // 1) Fondo: cielo en gradiente + sol + nubes.
        renderizarFondo();

        // 2) Tuberias (solo en JUGANDO o GAMEOVER se ven; en INICIO no hay).
        for (Pipe t : tuberias) {
            t.render(renderer);
        }

        // 3) Suelo en franja inferior.
        renderizarSuelo();

        // 4) Pajaros. Los inactivos no se dibujan.
        jugador1.render(renderer);
        if (jugadoresActivos >= 2) jugador2.render(renderer);
        if (jugadoresActivos >= 3) jugador3.render(renderer);

        // 5) HUD con puntajes, nivel y posibles overlays. Sabe que jugadores
        //    estan activos y, si hay VICTORIA, quien gano.
        hud.render(renderer, jugador1, jugador2, jugador3,
                   jugadoresActivos, nivelActual, ganador, estado);
    }

    /**
     * Cielo con degradado vertical, sol y nubes.
     */
    private void renderizarFondo() {
        // Cielo: gradiente del azul claro abajo a un azul mas saturado arriba.
        renderer.dibujarRectGradiente(
            0.0f, 0.0f, 2.0f, 2.0f,
            0.75f, 0.92f, 1.00f,   // abajo (cerca del horizonte) mas claro
            0.30f, 0.58f, 0.92f);  // arriba mas saturado

        // Sol arriba a la derecha.
        renderer.dibujarCirculo(0.78f, 0.78f, 0.10f, 1.00f, 0.95f, 0.55f);
        // Halo del sol (un circulo mas grande y con tono mas suave).
        renderer.dibujarCirculo(0.78f, 0.78f, 0.13f, 1.00f, 0.93f, 0.65f);
        // Vuelvo a dibujar el sol encima asi se ve el "halo" alrededor.
        renderer.dibujarCirculo(0.78f, 0.78f, 0.09f, 1.00f, 0.96f, 0.45f);

        // Nubes: cada nube son 3 circulos blancos solapados.
        for (float[] n : nubes) {
            float x = n[0], y = n[1], r = n[2];
            renderer.dibujarCirculo(x,          y,        r,        1.0f, 1.0f, 1.0f);
            renderer.dibujarCirculo(x + r*0.8f, y - r*0.1f, r*0.8f, 1.0f, 1.0f, 1.0f);
            renderer.dibujarCirculo(x - r*0.8f, y - r*0.1f, r*0.7f, 1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * Suelo: un rectangulo de tierra y encima una franja de pasto verde.
     * El tope del pasto coincide exactamente con Bird.Y_SUELO, que es el limite
     * por debajo del cual el pajaro muere.
     */
    private void renderizarSuelo() {
        float topePasto   = Bird.Y_SUELO;
        float topeTierra  = topePasto - PASTO_ALTO;
        // Tierra: del fondo de la pantalla hasta el inicio del pasto.
        float altoTierra   = topeTierra - (-1.0f);
        float yCentroTierra = -1.0f + altoTierra * 0.5f;
        renderer.dibujarRect(0.0f, yCentroTierra, 2.0f, altoTierra,
                             0.55f, 0.36f, 0.18f);
        // Pasto: franja encima de la tierra, su tope queda en Bird.Y_SUELO.
        float yCentroPasto = topeTierra + PASTO_ALTO * 0.5f;
        renderer.dibujarRect(0.0f, yCentroPasto, 2.0f, PASTO_ALTO,
                             0.30f, 0.70f, 0.25f);
    }

    // ============================================================
    // Titulo de ventana (mensajes de ayuda y dificultad)
    // ============================================================

    public void actualizarTitulo() {
        String estadoTxt = switch (estado) {
            case SELECCION -> "Elegi: 1, 2 o 3 jugadores";
            case INICIO    -> switch (jugadoresActivos) {
                case 1 -> "SPACE para jugar";
                case 2 -> "SPACE (J1) / W (J2) para jugar";
                case 3 -> "SPACE (J1) / W (J2) / UP (J3) para jugar";
                default -> "Listo para jugar";
            };
            case JUGANDO   -> "Nivel " + (nivelActual + 1) + " | Meta: " + META_PUNTOS;
            case GAMEOVER  -> "GAME OVER - R/SPACE = repetir, ENTER = menu";
            case VICTORIA  -> "GANO J" + ganador + " - R/SPACE = repetir, ENTER = menu";
        };
        StringBuilder marcador = new StringBuilder("J1: ").append(jugador1.getPuntaje());
        if (jugadoresActivos >= 2) marcador.append(" | J2: ").append(jugador2.getPuntaje());
        if (jugadoresActivos >= 3) marcador.append(" | J3: ").append(jugador3.getPuntaje());
        String titulo = String.format("Flappy Bird OpenGL | %s | %s", marcador, estadoTxt);
        GLFW.glfwSetWindowTitle(window, titulo);
    }

    // Getters por si AppFlappyBird necesita consultarlos
    public Estado getEstado() { return estado; }
}
