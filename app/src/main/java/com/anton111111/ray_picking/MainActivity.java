package com.anton111111.ray_picking;

import android.opengl.GLES20;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.os.Bundle;
import android.widget.TextView;

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;

public class MainActivity extends GvrActivity
        implements GvrView.StereoRenderer {

    public static final float Z_NEAR = 0.1f;
    public static final float Z_FAR = 100.0f;
    public static final float CURSOR_Z = -0.5f;


    private final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";
    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private short[] vertexIndexes = new short[]{
            0, 1, 2, 0, 2, 3
    };


    private static final float colors[] = {
            1.0f, 0.5f, 0.5f, 0.5f,
            0.5f, 1.0f, 0.5f, 0.5f,
            0.5f, 0.5f, 1.0f, 0.5f,
            1.0f, 1.0f, 1.0f, 1.0f,
    };

    private static final String name_of_colors[] = {
            "red",
            "green",
            "blue",
            "white",
    };

    private float[] viewMatrix = new float[16];
    private float[] eulerAngles = new float[3];
    private float[] modelViewProjection = new float[16];
    private FloatBuffer[] vertexCoordsBuf = new FloatBuffer[3];
    private ShortBuffer indexesBuffer;
    private int mProgram;
    private int mPositionHandle;
    private int mColorHandle;
    private int mMVPMatrixHandle;
    private FloatBuffer cursorVertexCoordsBuf;
    private ArrayList<float[]> objects = new ArrayList<>();
    private int viewWidth;
    private int viewHeight;
    private float[] perspectiveMatrix = new float[16];
    private float[] modelViewMatrix = new float[16];
    private TextView infoBoxView;
    private String picked;
    private float[] cursorCoords;
    private float cursorX = -1;
    private float cursorY = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        infoBoxView = findViewById(R.id.info_box);
        GvrView gvrView = findViewById(R.id.gvr_view);
        //Change stereo mode
        gvrView.setStereoModeEnabled(true);
        gvrView.setRenderer(this);
        setGvrView(gvrView);
    }


    @Override
    public void onNewFrame(HeadTransform headTransform) {
        Matrix.setLookAtM(viewMatrix, 0,
                0.0f, 0.0f, 0.01f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);

        headTransform.getEulerAngles(eulerAngles, 0);
    }

    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        perspectiveMatrix = eye.getPerspective(Z_NEAR, Z_FAR);
        modelViewMatrix = new float[16];
        Matrix.setIdentityM(modelViewMatrix, 0);

        float theta = -eulerAngles[1];
        float phi = -eulerAngles[0] + 1.57f;
        float anglePitch = (float) Math.toDegrees(phi) - 90.0f;
        float angleYaw = (float) Math.toDegrees(theta);

        Matrix.rotateM(modelViewMatrix, 0, anglePitch, 1.0f, 0.0f, 0.0f);
        Matrix.rotateM(modelViewMatrix, 0, angleYaw, 0.0f, 1.0f, 0.0f);
        Matrix.multiplyMM(modelViewMatrix, 0, modelViewMatrix, 0, viewMatrix, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspectiveMatrix, 0, modelViewMatrix, 0);

        for (int i = 0; i < 3; i++) {
            renderObject(modelViewProjection, vertexCoordsBuf[i], i);
        }

        renderCursor(eye);

        if (eye.getType() <= 1 && cursorX >= 0 && cursorY >= 0) {
            picked = "";
            for (float[] object : objects) {
                if (rayPicking(viewWidth, viewHeight, cursorX, cursorY,
                        viewMatrix, perspectiveMatrix, modelViewMatrix,
                        object, vertexIndexes)) {
                    picked += (picked.length() > 0) ? "," : "";
                    picked += " " + name_of_colors[objects.indexOf(object)];
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    infoBoxView.setText("Picked: " + picked);
                }
            });
        }
    }

    private void renderCursor(Eye eye) {
        GLES20.glDepthFunc(GLES20.GL_ALWAYS);
        GLES20.glDepthMask(false);

        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(
                mPositionHandle, 3,
                GLES20.GL_FLOAT, false,
                12, cursorVertexCoordsBuf);


        GLES20.glUniform4fv(mColorHandle,
                1, colors, 12);

        float[] perspectiveMatrix = eye.getPerspective(Z_NEAR, Z_FAR);
        float[] orthoViewMatrix = new float[16];
        float[] modelViewProjMatrix = new float[16];
        Matrix.orthoM(orthoViewMatrix, 0, -1, 1, -1, 1, Z_NEAR, Z_FAR);
        Matrix.multiplyMM(modelViewProjMatrix, 0, perspectiveMatrix, 0, orthoViewMatrix, 0);

        GLES20.glUniformMatrix4fv(mMVPMatrixHandle,
                1, false, modelViewProjMatrix, 0);


        // Draw the square
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, 6,
                GLES20.GL_UNSIGNED_SHORT, indexesBuffer);
        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDepthMask(true);

        if (eye.getType() <= 1 &&
                (cursorX < 0 || cursorY < 0)) {
            calculateCursorScreenCoords(modelViewProjMatrix);
        }
    }


    private void calculateCursorScreenCoords(float[] modelViewProjMatrix) {
        if (!getGvrView().getStereoModeEnabled()) {
            cursorX = (float) viewWidth / 2.0f;
            cursorY = (float) viewHeight / 2.0f;
            return;
        }
        float[] coords = new float[]{
                cursorCoords[0] + (((cursorCoords[3] + 1.0f) - (cursorCoords[0] + 1.0f)) / 2.0f),
                cursorCoords[1] + (((cursorCoords[4] + 1.0f) - (cursorCoords[1] + 1.0f)) / 2.0f),
                CURSOR_Z,
                1.0f
        };
        float[] result = new float[16];
        Matrix.multiplyMV(result, 0, modelViewProjMatrix, 0, coords, 0);
        float xndc = result[0] / result[3];
        float yndc = result[1] / result[3];
        cursorX = (xndc + 1.0f) * ((float) viewWidth / 2.0f);
        cursorY = (yndc + 1.0f) * ((float) viewHeight / 2.0f);
    }

    private void renderObject(float[] modelViewProjection, FloatBuffer vertexCoordsBuf, int i) {
        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(
                mPositionHandle, 3,
                GLES20.GL_FLOAT, false,
                12, vertexCoordsBuf);


        GLES20.glUniform4fv(mColorHandle,
                1, colors, i * 4);

        GLES20.glUniformMatrix4fv(mMVPMatrixHandle,
                1, false, modelViewProjection, 0);

        // Draw the square
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, 6,
                GLES20.GL_UNSIGNED_SHORT, indexesBuffer);
        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
    }


    public boolean rayPicking(int viewWidth, int viewHeight, float rx, float ry,
                              float[] viewMatrix, float[] projMatrix, float[] modelViewMatrix,
                              float[] objectCoords, short[] objectIndexes) {
        float[] near_xyz = unProject(rx, ry, 0, viewMatrix, projMatrix, viewWidth, viewHeight);
        float[] far_xyz = unProject(rx, ry, 1, viewMatrix, projMatrix, viewWidth, viewHeight);
        int coordCount = objectCoords.length;
        float[] convertedSquare = new float[coordCount];
        float[] resultVector = new float[4];
        float[] inputVector = new float[4];
        for (int i = 0; i < coordCount; i = i + 3) {
            inputVector[0] = objectCoords[i];
            inputVector[1] = objectCoords[i + 1];
            inputVector[2] = objectCoords[i + 2];
            inputVector[3] = 1;
            Matrix.multiplyMV(resultVector, 0, modelViewMatrix, 0, inputVector, 0);
            convertedSquare[i] = resultVector[0] / resultVector[3];
            convertedSquare[i + 1] = resultVector[1] / resultVector[3];
            convertedSquare[i + 2] = resultVector[2] / resultVector[3];
        }


        ArrayList<Triangle> triangles = new ArrayList<>();
        for (int i = 0; i < objectIndexes.length; i = i + 3) {
            int i1 = objectIndexes[i] * 3;
            int i2 = objectIndexes[i + 1] * 3;
            int i3 = objectIndexes[i + 2] * 3;
            triangles.add(
                    new Triangle(
                            new float[]{
                                    convertedSquare[i1],
                                    convertedSquare[i1 + 1],
                                    convertedSquare[i1 + 2]
                            },
                            new float[]{
                                    convertedSquare[i2],
                                    convertedSquare[i2 + 1],
                                    convertedSquare[i2 + 2]
                            },
                            new float[]{
                                    convertedSquare[i3],
                                    convertedSquare[i3 + 1],
                                    convertedSquare[i3 + 2]
                            }
                    )
            );
        }

        for (Triangle t : triangles) {
            float[] point = new float[3];
            int intersects = Triangle.intersectRayAndTriangle(near_xyz, far_xyz, t, point);
            if (intersects == 1 || intersects == 2) {
                return true;
            }
        }
        return false;
    }

    private float[] unProject(float xTouch, float yTouch, float winz,
                              float[] viewMatrix,
                              float[] projMatrix,
                              int width, int height) {
        int[] viewport = {0, 0, width, height};

        float[] out = new float[3];
        float[] temp = new float[4];
        float[] temp2 = new float[4];
        // get the near and far ords for the click
        float winx = xTouch, winy = (float) viewport[3] - yTouch;

        int result = GLU.gluUnProject(winx, winy, winz, viewMatrix, 0, projMatrix, 0, viewport, 0, temp, 0);

        Matrix.multiplyMV(temp2, 0, viewMatrix, 0, temp, 0);
        if (result == 1) {
            out[0] = temp2[0] / temp2[3];
            out[1] = temp2[1] / temp2[3];
            out[2] = temp2[2] / temp2[3];
        }
        return out;
    }

    @Override
    public void onFinishFrame(Viewport viewport) {

    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        viewWidth = width;
        viewHeight = height;
    }

    @Override
    public void onSurfaceCreated(EGLConfig eglConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f);

        for (int i = 0; i < 3; i++) {
            float width = new Random().nextFloat() * 0.2f + 0.1f;
            float height = new Random().nextFloat() * 0.2f + 0.1f;
            float _x = new Random().nextFloat() * 0.7f - 0.35f;
            float _y = new Random().nextFloat() * 0.7f - 0.35f;
            float _z = new Random().nextFloat() * 0.7f * -1.0f;
            float[] coords = new float[]{
                    _x, _y, _z,
                    _x + width, _y, _z,
                    _x + width, _y + height, _z,
                    _x, _y + height, _z
            };
            objects.add(coords);
            ByteBuffer bbcv = ByteBuffer.allocateDirect(coords.length * 4);
            bbcv.order(ByteOrder.nativeOrder());
            vertexCoordsBuf[i] = bbcv.asFloatBuffer();
            vertexCoordsBuf[i].put(coords);
            vertexCoordsBuf[i].position(0);
        }

        float cursorSize = 0.005f;
        cursorCoords = new float[]{
                0.0f - cursorSize, 0.0f - cursorSize, CURSOR_Z,
                0.0f + cursorSize, 0.0f - cursorSize, CURSOR_Z,
                0.0f + cursorSize, 0.0f + cursorSize, CURSOR_Z,
                0.0f - cursorSize, 0.0f + cursorSize, CURSOR_Z
        };

        ByteBuffer bbcv = ByteBuffer.allocateDirect(cursorCoords.length * 4);
        bbcv.order(ByteOrder.nativeOrder());
        cursorVertexCoordsBuf = bbcv.asFloatBuffer();
        cursorVertexCoordsBuf.put(cursorCoords);
        cursorVertexCoordsBuf.position(0);

        ByteBuffer bbSVIB = ByteBuffer.allocateDirect(vertexIndexes.length * 2);
        bbSVIB.order(ByteOrder.nativeOrder());
        indexesBuffer = bbSVIB.asShortBuffer();
        indexesBuffer.put(vertexIndexes);
        indexesBuffer.position(0);

        // prepare shaders and OpenGL program
        int vertexShader = loadShader(
                GLES20.GL_VERTEX_SHADER,
                vertexShaderCode);
        int fragmentShader = loadShader(
                GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

    }

    public static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void onRendererShutdown() {

    }


}
