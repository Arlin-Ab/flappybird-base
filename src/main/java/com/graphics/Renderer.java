package com.graphics;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Renderer:
 * Encargada de los shaders y el renderizado de primitivas.
 * Centraliza la creación del pipeline gráfico (VBOs, VAOs, Shaders, Uniforms)
 * y provee métodos de dibujo de alto nivel para el resto del juego.
 *
 * Primitivas soportadas:
 *  - Rectángulo (quad de 2 triángulos).
 *  - Elipse/Círculo (triangle fan con N segmentos).
 *  - Triángulo direccional (para pico, cola).
 *  - Ala (quad con pivote en el borde inferior para rotación de aleteo).
 *
 * El vertex shader aplica: escalado → rotación → traslación.
 * La rotación ocurre alrededor del origen local (0,0) de la primitiva.
 */
public class Renderer {

    // ─── Programa de shaders ────────────────────────────────────────────────
    /** Identificador del programa de shaders enlazado en la GPU. */
    private int programa;

    // ─── Localizaciones de uniforms en el shader ────────────────────────────
    /** Uniform vec2: desplazamiento en NDC de la primitiva. */
    private int uOffset;
    /** Uniform vec2: escala (ancho, alto) de la primitiva. */
    private int uScale;
    /** Uniform vec3: color RGB de la primitiva. */
    private int uColor;
    /** Uniform float: ángulo de rotación en radianes. */
    private int uRotacion;

    // ─── VAOs y VBOs para cada primitiva ────────────────────────────────────
    /** VAO del quad unitario (rectángulo centrado de -0.5 a 0.5). */
    private int vaoQuad;
    /** VBO del quad unitario. */
    private int vboQuad;

    /** VAO del círculo unitario (radio 1, triangle fan). */
    private int vaoCirculo;
    /** VBO del círculo unitario. */
    private int vboCirculo;
    /** Cantidad de vértices del círculo (centro + N segmentos + cierre). */
    private int numVerticesCirculo;

    /** VAO del triángulo direccional (apunta a la derecha). */
    private int vaoTriangulo;
    /** VBO del triángulo direccional. */
    private int vboTriangulo;

    /** VAO del ala (quad con pivote en borde inferior y=0). */
    private int vaoAla;
    /** VBO del ala. */
    private int vboAla;

    // ─── Constantes ─────────────────────────────────────────────────────────
    /** Número de segmentos para aproximar círculos/elipses. */
    private static final int SEGMENTOS_CIRCULO = 30;

    // ═══════════════════════════════════════════════════════════════════════
    // INICIALIZACIÓN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Crea los shaders y toda la geometría base (quads, círculos, triángulos).
     * Debe llamarse una sola vez después de tener el contexto OpenGL activo.
     */
    public void inicializar() {
        crearShaders();
        crearQuadBase();
        crearCirculoBase();
        crearTrianguloBase();
        crearAlaBase();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CREACIÓN DE SHADERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Compila y enlaza los shaders del pipeline 2D.
     *
     * Vertex Shader:
     *   Recibe posición local del vértice (aPos).
     *   Aplica escala (uScale), rotación alrededor del origen (uRotacion)
     *   y finalmente traslación (uOffset) para posicionar en NDC.
     *
     * Fragment Shader:
     *   Asigna color uniforme (uColor) a cada fragmento.
     */
    private void crearShaders() {
        // ── Código GLSL del Vertex Shader ──
        // La transformación se aplica en este orden:
        //   1. Escalar: multiplicar aPos.xy por uScale.
        //   2. Rotar: aplicar matriz de rotación 2D con uRotacion.
        //   3. Trasladar: sumar uOffset.
        // El resultado se asigna a gl_Position (salida estándar de OpenGL).
        String vertexSrc = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform vec2 uOffset;
            uniform vec2 uScale;
            uniform float uRotacion;
            void main() {
                vec2 escalado = aPos.xy * uScale;
                float c = cos(uRotacion);
                float s = sin(uRotacion);
                vec2 rotado = vec2(
                    escalado.x * c - escalado.y * s,
                    escalado.x * s + escalado.y * c
                );
                vec2 posFinal = rotado + uOffset;
                gl_Position = vec4(posFinal, aPos.z, 1.0);
            }
            """;

        // ── Código GLSL del Fragment Shader ──
        // Simplemente pinta cada píxel con el color uniforme recibido.
        // uColor es un vec3 (R, G, B). Alpha se fija en 1.0 (opaco).
        String fragmentSrc = """
            #version 330 core
            uniform vec3 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(uColor, 1.0);
            }
            """;

        // Compilar vertex shader.
        // glCreateShader crea un objeto shader vacío en la GPU.
        // glShaderSource le asigna el código fuente GLSL.
        // glCompileShader lo compila en la GPU.
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSrc);
        GL20.glCompileShader(vertexShader);
        comprobarShader(vertexShader, "Vertex");

        // Compilar fragment shader (mismo proceso).
        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSrc);
        GL20.glCompileShader(fragmentShader);
        comprobarShader(fragmentShader, "Fragment");

        // Enlazar ambos shaders en un programa.
        // glCreateProgram crea un contenedor.
        // glAttachShader añade cada shader compilado.
        // glLinkProgram resuelve las conexiones entre ellos.
        programa = GL20.glCreateProgram();
        GL20.glAttachShader(programa, vertexShader);
        GL20.glAttachShader(programa, fragmentShader);
        GL20.glLinkProgram(programa);

        if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException(
                "Error al enlazar programa: " + GL20.glGetProgramInfoLog(programa)
            );
        }

        // Obtener las localizaciones (IDs internos) de cada uniform.
        // Estos IDs se usan después con glUniform para enviar datos al shader.
        uOffset   = GL20.glGetUniformLocation(programa, "uOffset");
        uScale    = GL20.glGetUniformLocation(programa, "uScale");
        uColor    = GL20.glGetUniformLocation(programa, "uColor");
        uRotacion = GL20.glGetUniformLocation(programa, "uRotacion");

        if (uOffset == -1 || uScale == -1 || uColor == -1 || uRotacion == -1) {
            throw new RuntimeException("No se pudieron obtener uniforms del shader");
        }

        // Liberar los shaders individuales: ya están copiados dentro del programa.
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    /**
     * Verifica que un shader compiló correctamente.
     * Si falla, lanza excepción con el log de error de la GPU.
     *
     * @param shader ID del shader en la GPU.
     * @param tipo   Etiqueta descriptiva ("Vertex" o "Fragment").
     */
    private void comprobarShader(int shader, String tipo) {
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException(
                tipo + " shader: " + GL20.glGetShaderInfoLog(shader)
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CREACIÓN DE GEOMETRÍAS BASE (VBOs + VAOs)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Crea un quad unitario (rectángulo) centrado en el origen.
     * Rango en X e Y: de -0.5 a 0.5.
     * Compuesto por 2 triángulos = 6 vértices.
     *
     * Este quad se reutiliza para dibujar cualquier rectángulo
     * aplicando escala y traslación mediante uniforms.
     */
    private void crearQuadBase() {
        float[] vertices = {
            // Triángulo 1
            -0.5f, -0.5f, 0.0f,
             0.5f, -0.5f, 0.0f,
             0.5f,  0.5f, 0.0f,
            // Triángulo 2
            -0.5f, -0.5f, 0.0f,
             0.5f,  0.5f, 0.0f,
            -0.5f,  0.5f, 0.0f
        };

        vaoQuad = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoQuad);

        vboQuad = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboQuad);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        // Configurar atributo de posición (location = 0): 3 floats por vértice.
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Crea un círculo unitario (radio = 1) centrado en el origen.
     * Usa GL_TRIANGLE_FAN: el primer vértice es el centro,
     * los siguientes recorren el perímetro en sentido antihorario.
     *
     * Para dibujar una elipse, se escala de forma no uniforme (uScale.x != uScale.y).
     */
    private void crearCirculoBase() {
        // Calcular vértices del círculo aproximado con SEGMENTOS_CIRCULO triángulos.
        // Estructura del fan: [centro, p0, p1, p2, ..., pN-1, p0]
        // Total: 1 (centro) + SEGMENTOS_CIRCULO + 1 (cierre) = SEGMENTOS_CIRCULO + 2
        numVerticesCirculo = SEGMENTOS_CIRCULO + 2;
        float[] vertices = new float[numVerticesCirculo * 3];

        // Vértice 0: centro del círculo (0, 0, 0)
        vertices[0] = 0.0f;
        vertices[1] = 0.0f;
        vertices[2] = 0.0f;

        // Vértices 1 a SEGMENTOS_CIRCULO: puntos en el perímetro
        for (int i = 0; i < SEGMENTOS_CIRCULO; i++) {
            double angulo = 2.0 * Math.PI * i / SEGMENTOS_CIRCULO;
            int idx = (i + 1) * 3;
            vertices[idx]     = (float) Math.cos(angulo);
            vertices[idx + 1] = (float) Math.sin(angulo);
            vertices[idx + 2] = 0.0f;
        }

        // Último vértice: repetir el primero del perímetro para cerrar el fan.
        int idxCierre = (SEGMENTOS_CIRCULO + 1) * 3;
        vertices[idxCierre]     = 1.0f;
        vertices[idxCierre + 1] = 0.0f;
        vertices[idxCierre + 2] = 0.0f;

        vaoCirculo = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoCirculo);

        vboCirculo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboCirculo);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Crea un triángulo direccional que apunta hacia la derecha.
     * La base (borde izquierdo) mide 1 unidad de alto,
     * la punta se extiende 1 unidad hacia la derecha desde el centro.
     *
     * Se usa para dibujar el pico (rotación = 0) y la cola (rotación = PI).
     */
    private void crearTrianguloBase() {
        // Triángulo apuntando a la derecha, base centrada en X = -0.5.
        float[] vertices = {
            -0.5f,  0.5f, 0.0f,  // esquina superior de la base
             0.5f,  0.0f, 0.0f,  // punta (derecha)
            -0.5f, -0.5f, 0.0f,  // esquina inferior de la base
        };

        vaoTriangulo = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoTriangulo);

        vboTriangulo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTriangulo);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Crea un quad para el ala con el pivote de rotación en el borde inferior (y = 0).
     * Esto permite que el ala rote alrededor de su punto de unión al cuerpo.
     * El quad va de y=0 (base) a y=1 (punta) en coordenadas locales.
     */
    private void crearAlaBase() {
        // Quad desde y=0 (pivote) hasta y=1 (extremo superior).
        float[] vertices = {
            // Triángulo 1
            -0.5f, 0.0f, 0.0f,  // inferior izquierda (pivote)
             0.5f, 0.0f, 0.0f,  // inferior derecha (pivote)
             0.5f, 1.0f, 0.0f,  // superior derecha
            // Triángulo 2
            -0.5f, 0.0f, 0.0f,  // inferior izquierda
             0.5f, 1.0f, 0.0f,  // superior derecha
            -0.5f, 1.0f, 0.0f,  // superior izquierda
        };

        vaoAla = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoAla);

        vboAla = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboAla);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MÉTODOS DE DIBUJO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Activa el programa de shaders. Debe llamarse antes de cualquier dibujo.
     * Vincula el programa en la GPU para que los siguientes glDrawArrays
     * usen estos shaders.
     */
    public void usarShader() {
        GL20.glUseProgram(programa);
    }

    /**
     * Dibuja un rectángulo en las coordenadas NDC especificadas.
     *
     * @param x        Centro X en NDC (Normalized Device Coordinates, -1 a 1).
     * @param y        Centro Y en NDC.
     * @param ancho    Ancho total del rectángulo.
     * @param alto     Alto total del rectángulo.
     * @param r        Componente rojo (0.0 a 1.0).
     * @param g        Componente verde (0.0 a 1.0).
     * @param b        Componente azul (0.0 a 1.0).
     * @param rotacion Ángulo de rotación en radianes (alrededor del centro).
     */
    public void dibujarRectangulo(float x, float y, float ancho, float alto,
                                   float r, float g, float b, float rotacion) {
        GL20.glUniform2f(uOffset, x, y);
        GL20.glUniform2f(uScale, ancho, alto);
        GL20.glUniform3f(uColor, r, g, b);
        GL20.glUniform1f(uRotacion, rotacion);
        GL30.glBindVertexArray(vaoQuad);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    /**
     * Dibuja una elipse (o círculo si radioX == radioY) usando triangle fan.
     *
     * @param x        Centro X en NDC.
     * @param y        Centro Y en NDC.
     * @param radioX   Semieje horizontal.
     * @param radioY   Semieje vertical.
     * @param r        Componente rojo.
     * @param g        Componente verde.
     * @param b        Componente azul.
     * @param rotacion Ángulo de rotación en radianes.
     */
    public void dibujarElipse(float x, float y, float radioX, float radioY,
                               float r, float g, float b, float rotacion) {
        GL20.glUniform2f(uOffset, x, y);
        GL20.glUniform2f(uScale, radioX, radioY);
        GL20.glUniform3f(uColor, r, g, b);
        GL20.glUniform1f(uRotacion, rotacion);
        GL30.glBindVertexArray(vaoCirculo);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, numVerticesCirculo);
    }

    /**
     * Dibuja una elipse sin rotación (conveniencia).
     */
    public void dibujarElipse(float x, float y, float radioX, float radioY,
                               float r, float g, float b) {
        dibujarElipse(x, y, radioX, radioY, r, g, b, 0.0f);
    }

    /**
     * Dibuja un triángulo direccional.
     * Sin rotación adicional, apunta hacia la derecha.
     *
     * @param x        Centro X en NDC.
     * @param y        Centro Y en NDC.
     * @param ancho    Ancho total del triángulo.
     * @param alto     Alto total del triángulo.
     * @param r        Rojo.
     * @param g        Verde.
     * @param b        Azul.
     * @param rotacion Rotación en radianes.
     */
    public void dibujarTriangulo(float x, float y, float ancho, float alto,
                                  float r, float g, float b, float rotacion) {
        GL20.glUniform2f(uOffset, x, y);
        GL20.glUniform2f(uScale, ancho, alto);
        GL20.glUniform3f(uColor, r, g, b);
        GL20.glUniform1f(uRotacion, rotacion);
        GL30.glBindVertexArray(vaoTriangulo);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /**
     * Dibuja el ala del pájaro. El pivote de rotación está en el borde inferior
     * de la primitiva (donde se une al cuerpo).
     *
     * @param x        Posición X del punto de unión al cuerpo.
     * @param y        Posición Y del punto de unión al cuerpo.
     * @param ancho    Ancho del ala.
     * @param alto     Largo del ala (desde el pivote hacia afuera).
     * @param r        Rojo.
     * @param g        Verde.
     * @param b        Azul.
     * @param rotacion Ángulo de aleteo en radianes.
     */
    public void dibujarAla(float x, float y, float ancho, float alto,
                            float r, float g, float b, float rotacion) {
        GL20.glUniform2f(uOffset, x, y);
        GL20.glUniform2f(uScale, ancho, alto);
        GL20.glUniform3f(uColor, r, g, b);
        GL20.glUniform1f(uRotacion, rotacion);
        GL30.glBindVertexArray(vaoAla);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    /**
     * Limpia la pantalla con un color de fondo sólido.
     *
     * @param r Rojo (0.0 a 1.0).
     * @param g Verde (0.0 a 1.0).
     * @param b Azul (0.0 a 1.0).
     */
    public void limpiarPantalla(float r, float g, float b) {
        GL11.glClearColor(r, g, b, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIMPIEZA DE RECURSOS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Libera todos los recursos OpenGL (VAOs, VBOs, programa).
     * Debe llamarse al cerrar la aplicación para evitar fugas de memoria en GPU.
     */
    public void liberar() {
        GL30.glDeleteVertexArrays(vaoQuad);
        GL30.glDeleteVertexArrays(vaoCirculo);
        GL30.glDeleteVertexArrays(vaoTriangulo);
        GL30.glDeleteVertexArrays(vaoAla);
        GL15.glDeleteBuffers(vboQuad);
        GL15.glDeleteBuffers(vboCirculo);
        GL15.glDeleteBuffers(vboTriangulo);
        GL15.glDeleteBuffers(vboAla);
        GL20.glDeleteProgram(programa);
    }
}
