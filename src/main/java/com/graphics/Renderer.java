package com.graphics;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Renderer:
 * Encargado de todo el renderizado gráfico del juego.
 *
 * Responsabilidades:
 * - Crear y gestionar shaders (vertex y fragment).
 * - Gestionar VAOs y VBOs para primitivas 2D.
 * - Dibujar rectángulos simples y figuras compuestas (pájaro).
 * - Dibujar fondo degradado y nubes.
 *
 * Pipeline gráfico utilizado:
 * 1. VAO (Vertex Array Object): almacena la configuración de vértices.
 * 2. VBO (Vertex Buffer Object): almacena los datos de posición en GPU.
 * 3. Shaders: transforman coordenadas y colorean pixels.
 *    - Vertex Shader: aplica transformación (escala + offset).
 *    - Fragment Shader: establece color sólido por objeto.
 * 4. Uniforms: variables que pasan datos de CPU a shaders en cada frame.
 */
public class Renderer {
    private int programa;
    private int vao;
    private int vbo;
    private int uOffsetLocation;
    private int uScaleLocation;
    private int uColorLocation;
    private int uRotationLocation;

    public int getUOffsetLocation() { return uOffsetLocation; }
    public int getUScaleLocation() { return uScaleLocation; }
    public int getUColorLocation() { return uColorLocation; }
    public int getURotationLocation() { return uRotationLocation; }
    public int getVao() { return vao; }
    public int getPrograma() { return programa; }

    /**
     * Inicializa el contexto OpenGL y crea el pipeline de shaders.
     */
    public void init() {
        crearShaders();
        crearQuadBase();
    }

    /**
     * Crea el programa de shaders:
     * - Vertex Shader: transforma posições 2D con escala, rotación y traslación.
     * - Fragment Shader: color uniforme.
     *
     * Los uniforms declarados son:
     * - uOffset: traslación (x, y) del objeto.
     * - uScale: escala (ancho, alto) del quad unitario.
     * - uColor: color RGB del objeto.
     * - uRotation: ángulo de rotación en radianes.
     */
    private void crearShaders() {
        String vertexSrc = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform vec2 uOffset;
            uniform vec2 uScale;
            uniform float uRotation;
            void main() {
                vec2 pos = aPos.xy;
                float cosR = cos(uRotation);
                float sinR = sin(uRotation);
                pos = vec2(pos.x * cosR - pos.y * sinR, pos.x * sinR + pos.y * cosR);
                vec2 finalPos = pos * uScale + uOffset;
                gl_Position = vec4(finalPos, aPos.z, 1.0);
            }
            """;

        String fragmentSrc = """
            #version 330 core
            uniform vec3 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(uColor, 1.0);
            }
            """;

        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSrc);
        GL20.glCompileShader(vertexShader);
        comprobarShader(vertexShader, "Vertex");

        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSrc);
        GL20.glCompileShader(fragmentShader);
        comprobarShader(fragmentShader, "Fragment");

        programa = GL20.glCreateProgram();
        GL20.glAttachShader(programa, vertexShader);
        GL20.glAttachShader(programa, fragmentShader);
        GL20.glLinkProgram(programa);

        if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Error al enlazar programa: " + GL20.glGetProgramInfoLog(programa));
        }

        uOffsetLocation = GL20.glGetUniformLocation(programa, "uOffset");
        uScaleLocation = GL20.glGetUniformLocation(programa, "uScale");
        uColorLocation = GL20.glGetUniformLocation(programa, "uColor");
        uRotationLocation = GL20.glGetUniformLocation(programa, "uRotation");

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    private void comprobarShader(int shader, String tipo) {
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException(tipo + " shader: " + GL20.glGetShaderInfoLog(shader));
        }
    }

    /**
     * Crea quad unitario centrado en origen:
     * - Rango x,y de -0.5 a +0.5.
     * - 2 triángulos (6 vértices).
     *
     * Este quad es la base reutilizable para dibujar cualquier rectángulo.
     * Se escala con uScale y se mueve con uOffset.
     */
    private void crearQuadBase() {
        float[] vertices = {
            -0.5f, -0.5f, 0.0f,
             0.5f, -0.5f, 0.0f,
             0.5f,  0.5f, 0.0f,
            -0.5f, -0.5f, 0.0f,
             0.5f,  0.5f, 0.0f,
            -0.5f,  0.5f, 0.0f
        };

        // VAO: Vertex Array Object - contenedor de configuración de vértices.
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        // VBO: Vertex Buffer Object - datos crudos de vértices en GPU.
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        // FloatBuffer: transferencia de datos Java -> GPU.
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        // Atributo 0: posición en el vertex shader (location = 0).
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Helper para dibujar rectángulo con color y rotación.
     */
    public void dibujarRect(float x, float y, float ancho, float alto, float r, float g, float b, float rotacion) {
        GL20.glUseProgram(programa);
        GL30.glBindVertexArray(vao);

        GL20.glUniform2f(uOffsetLocation, x, y);
        GL20.glUniform2f(uScaleLocation, ancho, alto);
        GL20.glUniform3f(uColorLocation, r, g, b);
        GL20.glUniform1f(uRotationLocation, rotacion);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    /**
     * Dibuja el fondo del juego: cielo degradado y nubes.
     * Las nubes son elipses simples usando primitivas de OpenGL.
     */
    public void dibujarFondo() {
        GL11.glClearColor(0.52f, 0.80f, 0.92f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(programa);
        GL30.glBindVertexArray(vao);

        dibujarNube(-0.7f, 0.65f, 0.25f);
        dibujarNube(0.3f, 0.75f, 0.20f);
        dibujarNube(0.8f, 0.55f, 0.18f);
        dibujarNube(-0.2f, 0.85f, 0.15f);
    }

    private void dibujarNube(float x, float y, float escala) {
        float[] color = {1f, 1f, 1f};
        GL20.glUniform3f(uColorLocation, color[0], color[1], color[2]);
        GL20.glUniform1f(uRotationLocation, 0f);

        GL20.glUniform2f(uOffsetLocation, x, y);
        GL20.glUniform2f(uScaleLocation, escala * 1.8f, escala * 0.7f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL20.glUniform2f(uOffsetLocation, x - escala * 0.6f, y - escala * 0.1f);
        GL20.glUniform2f(uScaleLocation, escala * 1.2f, escala * 0.6f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);

        GL20.glUniform2f(uOffsetLocation, x + escala * 0.5f, y - escala * 0.05f);
        GL20.glUniform2f(uScaleLocation, escala * 1.0f, escala * 0.55f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    /**
     * Dibuja una tubería (rectángulo verde).
     */
    public void dibujarTuberia(float x, float y, float ancho, float alto) {
        dibujarRect(x, y, ancho, alto, 0.18f, 0.70f, 0.25f, 0f);
    }

    /**
     * Dibuja el pájaro completo con todas sus partes:
     * - Cuerpo (rectángulo/elipse)
     * - Pico (triángulo)
     * - Ala (animada con bucle de aleteo)
     * - Cola
     * - Ojo con pupila
     *
     * El ángulo de rotación determina la inclinación del pájaro.
     */
    public void dibujarCuadroCompuesto(float x, float y, float ancho, float alto, float angulo,
            float[] colorCuerpo, float[] colorPico, float[] colorAla, float[] colorOjo,
            float[] colorPupila, float tiempoAla) {

        GL20.glUseProgram(programa);
        GL30.glBindVertexArray(vao);

        float w = ancho;
        float h = alto;

        float cosA = (float) Math.cos(angulo);
        float sinA = (float) Math.sin(angulo);

        dibujarRect(x, y, w * 0.9f, h * 0.85f, colorCuerpo[0], colorCuerpo[1], colorCuerpo[2], angulo);

        float picoX = x + cosA * (w * 0.5f) - sinA * (h * 0.05f);
        float picoY = y + sinA * (w * 0.5f) + cosA * (h * 0.05f);
        dibujarRect(picoX, picoY, w * 0.22f, h * 0.25f, colorPico[0], colorPico[1], colorPico[2], angulo);

        float alaX = x - cosA * (w * 0.05f) + sinA * (h * 0.1f);
        float alaY = y - sinA * (w * 0.05f) - cosA * (h * 0.1f);
        float alaOffset = (float) Math.sin(tiempoAla) * h * 0.15f;
        float alaXanim = alaX - sinA * alaOffset;
        float alaYanim = alaY + cosA * alaOffset;
        dibujarRect(alaXanim, alaYanim, w * 0.4f, h * 0.35f, colorAla[0], colorAla[1], colorAla[2], angulo);

        float colaX = x - cosA * (w * 0.45f) - sinA * (h * 0.1f);
        float colaY = y - sinA * (w * 0.45f) + cosA * (h * 0.1f);
        dibujarRect(colaX, colaY, w * 0.25f, h * 0.3f, colorCuerpo[0] * 0.85f, colorCuerpo[1] * 0.85f, colorCuerpo[2] * 0.85f, angulo);

        float ojoX = x + cosA * (w * 0.18f) - sinA * (h * 0.2f);
        float ojoY = y + sinA * (w * 0.18f) + cosA * (h * 0.2f);
        dibujarRect(ojoX, ojoY, w * 0.18f, h * 0.18f, colorOjo[0], colorOjo[1], colorOjo[2], angulo);

        float pupilaX = ojoX + cosA * (w * 0.03f) - sinA * (h * 0.02f);
        float pupilaY = ojoY + sinA * (w * 0.03f) + cosA * (h * 0.02f);
        dibujarRect(pupilaX, pupilaY, w * 0.08f, h * 0.08f, colorPupila[0], colorPupila[1], colorPupila[2], angulo);
    }

    public void cleanup() {
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vbo);
        GL20.glDeleteProgram(programa);
    }
}