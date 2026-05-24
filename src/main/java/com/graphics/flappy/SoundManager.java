package com.graphics.flappy;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * SoundManager:
 * Reproduce efectos de sonido SIN archivos .wav externos.
 *
 * Los tonos se sintetizan en memoria como ondas senoidales PCM 16-bit mono.
 * El JDK estandar incluye javax.sound.sampled, asi que no se necesita ninguna
 * libreria extra (ni OpenAL, ni LWJGL-OpenAL).
 *
 * Tipos de sonido:
 *   - SALTO:      beep agudo y corto (800 Hz, 80 ms).
 *   - PUNTO:      "ding" en dos frecuencias (1200 Hz + 1600 Hz, 100 ms total).
 *   - GAME_OVER:  tono descendente largo (400 Hz -> 150 Hz, 350 ms).
 *
 * IMPORTANTE - por que hay un POOL de clips por efecto:
 * --------------------------------------------------------
 * En la primera implementacion creaba un Clip nuevo en cada play() con
 * AudioSystem.getClip() + clip.open(...). Esas dos llamadas bloquean el
 * hilo entre 60 y 150 ms (la primera vez) y 30-80 ms despues, porque el
 * SO necesita reservar una "linea" del mixer (DirectSound/WASAPI en Win,
 * CoreAudio en Mac, ALSA/PulseAudio en Linux) y llenarla con los samples.
 *
 * A 60 FPS un frame dura ~16 ms. Bloquear el hilo 80 ms por cada salto =
 * 5 frames perdidos = "tirones" visibles cada vez que sonaba un efecto.
 *
 * Solucion: pre-abrir N clips por efecto en init(). En cada play() solo
 * hago stop() + setFramePosition(0) + start() sobre un Clip ya abierto.
 * Esas operaciones rondan 1-2 ms cada una, asi que no afectan el frame.
 *
 * Resiliencia: si el sistema no tiene tarjeta de sonido o falla el audio,
 * el manager queda en "modo silencioso" y los play() son no-op.
 */
public class SoundManager {

    // Parametros del formato de audio.
    // 44100 Hz: tasa estandar de calidad CD; 16 bits mono PCM lo mas simple
    // y portable. signed = true. bigEndian = false (little-endian, lo habitual).
    private static final float SAMPLE_RATE = 44100f;
    private static final AudioFormat FORMATO =
            new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    // Tamano del pool por efecto. Determina cuantos sonidos del mismo tipo
    // pueden solaparse antes de que uno robe la linea de otro.
    // Salto: pool grande porque puede dispararse muy rapido (dos saltos casi
    // simultaneos de J1 y J2). Punto: medio (cuando ambos pasan tuberia juntos).
    // Game over: solo 1 (no se solapa con sigo mismo).
    private static final int POOL_SALTO    = 4;
    private static final int POOL_PUNTO    = 2;
    private static final int POOL_GAMEOVER = 1;

    // Pools de clips pre-abiertos. Cada array tiene N copias del mismo sonido
    // listas para reproducir. Las clases ya estan en estado "open" desde init().
    private Clip[] poolSalto;
    private Clip[] poolPunto;
    private Clip[] poolGameOver;

    // Indices round-robin para rotar dentro de cada pool. La proxima
    // reproduccion de "salto" usa poolSalto[saltoIdx], luego incrementa.
    private int saltoIdx;
    private int puntoIdx;
    private int gameOverIdx;

    // Si la inicializacion del audio falla, queda en true y los play() no
    // hacen nada (no rompen el juego en sistemas sin audio).
    private boolean silencioso;

    /**
     * Genera los buffers y pre-abre los pools de clips. Llamar una vez al inicio.
     */
    public void init() {
        try {
            byte[] bufSalto    = generarSalto();
            byte[] bufPunto    = generarPunto();
            byte[] bufGameOver = generarGameOver();

            poolSalto    = crearPool(bufSalto,    POOL_SALTO);
            poolPunto    = crearPool(bufPunto,    POOL_PUNTO);
            poolGameOver = crearPool(bufGameOver, POOL_GAMEOVER);

            silencioso = false;
        } catch (Throwable t) {
            silencioso = true;
            System.err.println("SoundManager: audio no disponible, modo silencioso. " + t.getMessage());
        }
    }

    /**
     * Pre-abre n clips identicos con el mismo buffer PCM. Cada uno queda
     * listo para que start() lo reproduzca inmediatamente.
     */
    private Clip[] crearPool(byte[] datos, int n) throws Exception {
        Clip[] pool = new Clip[n];
        for (int i = 0; i < n; i++) {
            Clip c = AudioSystem.getClip();
            c.open(FORMATO, datos, 0, datos.length);
            pool[i] = c;
        }
        return pool;
    }

    // ============================================================
    // API publica
    // ============================================================

    public void playSalto() {
        if (silencioso) return;
        Clip c = poolSalto[saltoIdx];
        saltoIdx = (saltoIdx + 1) % poolSalto.length;
        reproducir(c);
    }

    public void playPunto() {
        if (silencioso) return;
        Clip c = poolPunto[puntoIdx];
        puntoIdx = (puntoIdx + 1) % poolPunto.length;
        reproducir(c);
    }

    public void playGameOver() {
        if (silencioso) return;
        Clip c = poolGameOver[gameOverIdx];
        gameOverIdx = (gameOverIdx + 1) % poolGameOver.length;
        reproducir(c);
    }

    /**
     * Reproduce un Clip ya abierto. Si todavia estaba sonando una vuelta
     * anterior, lo cortamos primero. setFramePosition(0) rebobina al inicio.
     * Estas tres operaciones suman ~2 ms vs. los 80 ms del open() original.
     */
    private void reproducir(Clip c) {
        try {
            if (c.isRunning()) c.stop();
            c.setFramePosition(0);
            c.start();
        } catch (Throwable t) {
            // Falla puntual: ignoro, no rompo el juego.
        }
    }

    /**
     * Libera todas las lineas de audio del sistema. Llamar al cerrar el juego.
     */
    public void cleanup() {
        cerrarPool(poolSalto);
        cerrarPool(poolPunto);
        cerrarPool(poolGameOver);
    }

    private void cerrarPool(Clip[] pool) {
        if (pool == null) return;
        for (Clip c : pool) {
            try {
                if (c.isRunning()) c.stop();
                c.close();
            } catch (Throwable t) {
                // ignoro: solo es cleanup.
            }
        }
    }

    // ============================================================
    // Generacion de los tonos
    // ============================================================

    /** Salto: tono agudo y corto. */
    private byte[] generarSalto() {
        return generarTono(800.0f, 0.08f, 0.35f, 0.005f, 0.020f);
    }

    /**
     * Punto: dos beeps encadenados a frecuencias distintas (efecto "ding-ding"
     * ascendente que suena a "logro").
     */
    private byte[] generarPunto() {
        byte[] a = generarTono(1200.0f, 0.05f, 0.30f, 0.003f, 0.015f);
        byte[] b = generarTono(1600.0f, 0.06f, 0.30f, 0.003f, 0.020f);
        byte[] todo = new byte[a.length + b.length];
        System.arraycopy(a, 0, todo, 0,         a.length);
        System.arraycopy(b, 0, todo, a.length,  b.length);
        return todo;
    }

    /**
     * Game over: tono largo cuya frecuencia desciende linealmente.
     * Se hace en una sola pasada cambiando freq en funcion del tiempo.
     */
    private byte[] generarGameOver() {
        float duracion = 0.35f;
        int numSamples = (int) (duracion * SAMPLE_RATE);
        byte[] datos = new byte[numSamples * 2];

        float freqInicial = 400f;
        float freqFinal   = 150f;
        // Para mantener la fase continua aunque la frecuencia cambie con t,
        // integro freq(t) acumulando fase paso a paso.
        double fase = 0.0;

        for (int i = 0; i < numSamples; i++) {
            float t = i / SAMPLE_RATE;
            float u = t / duracion; // 0..1
            float freq = freqInicial + (freqFinal - freqInicial) * u;
            fase += 2.0 * Math.PI * freq / SAMPLE_RATE;

            // Envelope: ataque rapido, decaimiento exponencial.
            float env = envolope(t, duracion, 0.01f, 0.30f);
            float amp = 0.35f;
            double sample = amp * env * Math.sin(fase);
            escribirSampleLE(datos, i, sample);
        }
        return datos;
    }

    /**
     * Genera un tono puro de frecuencia fija con envolope de ataque/decaimiento.
     *
     * @param freq      frecuencia en Hz.
     * @param duracion  duracion en segundos.
     * @param volumen   amplitud (0..1).
     * @param ataque    duracion del ataque (lineal 0 -> 1).
     * @param decaimiento duracion del decaimiento (exponencial al final).
     */
    private byte[] generarTono(float freq, float duracion, float volumen,
                               float ataque, float decaimiento) {
        int numSamples = (int) (duracion * SAMPLE_RATE);
        byte[] datos = new byte[numSamples * 2]; // 16-bit = 2 bytes/sample.

        for (int i = 0; i < numSamples; i++) {
            float t = i / SAMPLE_RATE;
            float env = envolope(t, duracion, ataque, decaimiento);
            double sample = volumen * env * Math.sin(2.0 * Math.PI * freq * t);
            escribirSampleLE(datos, i, sample);
        }
        return datos;
    }

    /**
     * Envolope simple ataque-sostenido-decaimiento:
     *   - de 0 a 'ataque': sube linealmente de 0 a 1.
     *   - de 'ataque' a 'duracion - decaimiento': constante en 1.
     *   - de ahi a 'duracion': cae exponencialmente.
     *
     * El envolope evita los "clicks" que se escuchan cuando el sonido empieza
     * o termina con una discontinuidad abrupta.
     */
    private float envolope(float t, float duracion, float ataque, float decaimiento) {
        if (t < ataque) {
            return t / ataque;
        }
        float inicioDecay = duracion - decaimiento;
        if (t < inicioDecay) {
            return 1.0f;
        }
        float u = (t - inicioDecay) / decaimiento; // 0..1
        // exp(-5u) cae rapido al final.
        return (float) Math.exp(-5.0 * u);
    }

    /**
     * Convierte un sample en [-1, 1] a 16-bit signed little-endian y lo
     * escribe en el indice i del buffer (cada sample ocupa 2 bytes).
     */
    private void escribirSampleLE(byte[] datos, int i, double sample) {
        if (sample >  1.0) sample =  1.0;
        if (sample < -1.0) sample = -1.0;
        short s = (short) (sample * Short.MAX_VALUE);
        datos[i * 2]     = (byte) (s & 0xFF);          // byte bajo
        datos[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);   // byte alto
    }
}
