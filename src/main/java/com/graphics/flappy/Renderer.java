package com.graphics.flappy;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Renderer:
 * Encapsula los recursos OpenGL (shaders, VAOs y VBOs) y expone metodos
 * simples para dibujar primitivas 2D: rectangulos, circulos y triangulos.
 *
 * Diseno:
 *  - Un unico programa de shader con uniforms para offset, escala, rotacion
 *    y color (con soporte opcional de degradado vertical).
 *  - Tres mallas reutilizables:
 *      * quadVAO:     rectangulo unitario centrado en (0,0), rango [-0.5, 0.5].
 *      * circleVAO:   circulo unitario radio 0.5 dibujado como triangle fan.
 *      * triangleVAO: triangulo unitario apuntando hacia arriba.
 *  - Cada metodo de dibujo solo cambia uniforms y emite un draw call.
 *
 * Esta clase NO conoce reglas del juego: solo dibuja primitivas.
 */
public class Renderer {

    // Cantidad de segmentos del circulo unitario. Mas segmentos = mas suave.
    private static final int SEGMENTOS_CIRCULO = 32;

    // Identificador del programa de shaders enlazado.
    private int programa;

    // Mallas reutilizables.
    private int quadVAO, quadVBO;
    private int circleVAO, circleVBO;
    private int triangleVAO, triangleVBO;

    // Cantidad de vertices del circulo (centro + N + cierre).
    private int verticesCirculo;

    // Locations de los uniforms del programa.
    private int uOffsetLoc;
    private int uScaleLoc;
    private int uRotLoc;
    private int uColorLoc;
    private int uColor2Loc;
    private int uUseGradientLoc;

    /**
     * Inicializa shaders y mallas. Debe llamarse despues de tener
     * el contexto OpenGL creado (GL.createCapabilities()).
     */
    public void init() {
        crearShaders();
        crearQuad();
        crearCirculo();
        crearTriangulo();
    }

    /**
     * Activa el programa de shader. Se llama una sola vez al principio del
     * render del frame; los metodos de dibujo asumen que el programa esta activo.
     */
    public void begin() {
        GL20.glUseProgram(programa);
    }

    // ============================================================
    // Metodos de dibujo
    // ============================================================

    /**
     * Dibuja un rectangulo de color solido.
     * @param x,y centro del rectangulo en coordenadas NDC.
     * @param ancho,alto tamano total en NDC.
     * @param rot rotacion en radianes (0 = sin rotar).
     */
    public void dibujarRect(float x, float y, float ancho, float alto, float rot,
                            float r, float g, float b) {
        configurarTransformacion(x, y, ancho, alto, rot);
        configurarColorSolido(r, g, b);
        GL30.glBindVertexArray(quadVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    /**
     * Version sin rotacion (rotacion = 0). Atajo para tuberias, suelo, etc.
     */
    public void dibujarRect(float x, float y, float ancho, float alto,
                            float r, float g, float b) {
        dibujarRect(x, y, ancho, alto, 0.0f, r, g, b);
    }

    /**
     * Dibuja un rectangulo con degradado vertical (color abajo -> color arriba).
     * Util para el cielo / fondo.
     */
    public void dibujarRectGradiente(float x, float y, float ancho, float alto,
                                     float rAbajo, float gAbajo, float bAbajo,
                                     float rArriba, float gArriba, float bArriba) {
        configurarTransformacion(x, y, ancho, alto, 0.0f);
        // uColor = abajo, uColor2 = arriba; el shader interpola con vLocal.y.
        GL20.glUniform3f(uColorLoc, rAbajo, gAbajo, bAbajo);
        GL20.glUniform3f(uColor2Loc, rArriba, gArriba, bArriba);
        GL20.glUniform1i(uUseGradientLoc, 1);
        GL30.glBindVertexArray(quadVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        // Apago el flag de gradiente para que las siguientes llamadas usen color solido.
        GL20.glUniform1i(uUseGradientLoc, 0);
    }

    /**
     * Dibuja un circulo (mediante un triangle fan).
     * @param x,y centro en NDC.
     * @param radio radio en NDC.
     */
    public void dibujarCirculo(float x, float y, float radio,
                               float r, float g, float b) {
        // La malla base tiene radio 0.5, asi que escalo a "radio * 2".
        configurarTransformacion(x, y, radio * 2.0f, radio * 2.0f, 0.0f);
        configurarColorSolido(r, g, b);
        GL30.glBindVertexArray(circleVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, verticesCirculo);
    }

    /**
     * Dibuja un triangulo unitario "apuntando hacia arriba" escalado y rotado.
     * Util para pico, cola y alas del pajaro.
     */
    public void dibujarTriangulo(float x, float y, float ancho, float alto, float rot,
                                 float r, float g, float b) {
        configurarTransformacion(x, y, ancho, alto, rot);
        configurarColorSolido(r, g, b);
        GL30.glBindVertexArray(triangleVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    // ============================================================
    // Helpers internos
    // ============================================================

    private void configurarTransformacion(float x, float y, float ancho, float alto, float rot) {
        GL20.glUniform2f(uOffsetLoc, x, y);
        GL20.glUniform2f(uScaleLoc, ancho, alto);
        GL20.glUniform1f(uRotLoc, rot);
    }

    private void configurarColorSolido(float r, float g, float b) {
        GL20.glUniform3f(uColorLoc, r, g, b);
        GL20.glUniform1i(uUseGradientLoc, 0);
    }

    // ============================================================
    // Construccion de shaders y mallas
    // ============================================================

    /**
     * Compila y enlaza el programa de shaders.
     * El vertex shader aplica escala, rotacion y traslacion al vertice;
     * el fragment shader elige color solido o degradado vertical
     * en funcion del flag uUseGradient.
     */
    private void crearShaders() {
        String vertexSrc = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform vec2 uOffset;
            uniform vec2 uScale;
            uniform float uRot;
            out vec2 vLocal; // posicion local del vertice (para gradiente)
            void main() {
                float c = cos(uRot);
                float s = sin(uRot);
                // Orden de transformaciones: ROTACION primero, ESCALA despues,
                // TRASLACION al final. Asi uScale.x siempre representa la
                // dimension HORIZONTAL en pantalla y uScale.y la VERTICAL,
                // independientemente de la rotacion aplicada.
                // 1) Rotacion en torno al origen local.
                vec2 rotated = vec2(aPos.x * c - aPos.y * s,
                                    aPos.x * s + aPos.y * c);
                // 2) Escalado (en ejes X/Y de la pantalla).
                vec2 scaled = rotated * uScale;
                // 3) Traslacion al punto deseado.
                vec2 finalPos = scaled + uOffset;
                gl_Position = vec4(finalPos, aPos.z, 1.0);
                vLocal = aPos.xy;
            }
            """;

        String fragmentSrc = """
            #version 330 core
            in vec2 vLocal;
            uniform vec3 uColor;
            uniform vec3 uColor2;
            uniform int uUseGradient;
            out vec4 fragColor;
            void main() {
                if (uUseGradient == 1) {
                    // vLocal.y va de -0.5 (abajo) a 0.5 (arriba); lo paso a [0,1].
                    float t = clamp(vLocal.y + 0.5, 0.0, 1.0);
                    fragColor = vec4(mix(uColor, uColor2, t), 1.0);
                } else {
                    fragColor = vec4(uColor, 1.0);
                }
            }
            """;

        int vs = compilarShader(GL20.GL_VERTEX_SHADER, vertexSrc, "Vertex");
        int fs = compilarShader(GL20.GL_FRAGMENT_SHADER, fragmentSrc, "Fragment");

        programa = GL20.glCreateProgram();
        GL20.glAttachShader(programa, vs);
        GL20.glAttachShader(programa, fs);
        GL20.glLinkProgram(programa);
        if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Error al enlazar programa: " + GL20.glGetProgramInfoLog(programa));
        }

        // Resolver locations de uniforms.
        uOffsetLoc = GL20.glGetUniformLocation(programa, "uOffset");
        uScaleLoc = GL20.glGetUniformLocation(programa, "uScale");
        uRotLoc = GL20.glGetUniformLocation(programa, "uRot");
        uColorLoc = GL20.glGetUniformLocation(programa, "uColor");
        uColor2Loc = GL20.glGetUniformLocation(programa, "uColor2");
        uUseGradientLoc = GL20.glGetUniformLocation(programa, "uUseGradient");

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private int compilarShader(int tipo, String src, String etiqueta) {
        int shader = GL20.glCreateShader(tipo);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException(etiqueta + " shader: " + GL20.glGetShaderInfoLog(shader));
        }
        return shader;
    }

    /**
     * Quad unitario centrado en el origen, rango [-0.5, +0.5] en X e Y.
     * Compuesto por 2 triangulos (6 vertices).
     */
    private void crearQuad() {
        float[] v = {
            -0.5f, -0.5f, 0.0f,
             0.5f, -0.5f, 0.0f,
             0.5f,  0.5f, 0.0f,
            -0.5f, -0.5f, 0.0f,
             0.5f,  0.5f, 0.0f,
            -0.5f,  0.5f, 0.0f
        };
        quadVAO = GL30.glGenVertexArrays();
        quadVBO = GL15.glGenBuffers();
        subirMalla(quadVAO, quadVBO, v);
    }

    /**
     * Triangulo unitario apuntando hacia arriba.
     * Base entre y=-0.5 y vertice superior en y=+0.5.
     */
    private void crearTriangulo() {
        float[] v = {
            -0.5f, -0.5f, 0.0f, // esquina inferior izquierda
             0.5f, -0.5f, 0.0f, // esquina inferior derecha
             0.0f,  0.5f, 0.0f  // vertice superior
        };
        triangleVAO = GL30.glGenVertexArrays();
        triangleVBO = GL15.glGenBuffers();
        subirMalla(triangleVAO, triangleVBO, v);
    }

    /**
     * Circulo unitario radio 0.5, como triangle fan.
     * Estructura: [centro, p0, p1, ..., pN, p0] (cierre repitiendo p0).
     */
    private void crearCirculo() {
        int n = SEGMENTOS_CIRCULO;
        verticesCirculo = n + 2; // centro + n puntos + repeticion de p0
        float[] v = new float[verticesCirculo * 3];
        int idx = 0;
        // Centro
        v[idx++] = 0.0f;
        v[idx++] = 0.0f;
        v[idx++] = 0.0f;
        for (int i = 0; i <= n; i++) {
            double ang = (2.0 * Math.PI * i) / n;
            v[idx++] = (float) (Math.cos(ang) * 0.5);
            v[idx++] = (float) (Math.sin(ang) * 0.5);
            v[idx++] = 0.0f;
        }
        circleVAO = GL30.glGenVertexArrays();
        circleVBO = GL15.glGenBuffers();
        subirMalla(circleVAO, circleVBO, v);
    }

    /**
     * Sube un arreglo de vertices a un VAO/VBO con un solo atributo de 3 floats
     * (posicion XYZ) en location 0.
     */
    private void subirMalla(int vao, int vbo, float[] datos) {
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        FloatBuffer buf = BufferUtils.createFloatBuffer(datos.length);
        buf.put(datos).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Libera todos los recursos GPU asociados al renderer.
     */
    public void cleanup() {
        GL30.glDeleteVertexArrays(quadVAO);
        GL30.glDeleteVertexArrays(circleVAO);
        GL30.glDeleteVertexArrays(triangleVAO);
        GL15.glDeleteBuffers(quadVBO);
        GL15.glDeleteBuffers(circleVBO);
        GL15.glDeleteBuffers(triangleVBO);
        GL20.glDeleteProgram(programa);
    }
}
