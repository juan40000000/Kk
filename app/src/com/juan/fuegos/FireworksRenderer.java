package com.juan.fuegos;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Render OpenGL ES 2.0: estelas con persistencia (FBO ping-pong), bloom en dos
 * niveles, cielo iluminado por las explosiones, humo volumétrico y reflejos en el agua.
 */
public class FireworksRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "Fuegos";

    private final Fireworks fw;
    private final float density;

    private int W = 1, H = 1;          // pantalla
    private int fwW = 1, fwH = 1;      // resolución de la capa de fuegos
    private int b1W, b1H, b2W, b2H;    // niveles de bloom

    private int progCopy, sceneTex, sceneFbo;
    private int progPoint, progFade, progDown, progBlur, progBg, progAdd, progSmoke;
    private int texSky, texCloud;
    private final int[] trailTex = new int[2], trailFbo = new int[2];
    private final int[] b1Tex = new int[2], b1Fbo = new int[2];
    private final int[] b2Tex = new int[2], b2Fbo = new int[2];
    private int cur = 0;
    private boolean fbosReady = false;

    private final FloatBuffer quad;
    private final float[] pts = new float[100000 * 7];
    private final FloatBuffer ptsBuf;
    private final float[] smoke = new float[Fireworks.SMAX * 8];
    private final FloatBuffer smokeBuf;
    private final float[] lightPos = new float[32], lightCol = new float[24];
    private float maxPoint = 64f;

    private long lastNs = 0;
    private float clock = 0;

    public FireworksRenderer(Fireworks fw, float density) {
        this.fw = fw;
        this.density = density;
        quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
        ptsBuf = ByteBuffer.allocateDirect(pts.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        smokeBuf = ByteBuffer.allocateDirect(smoke.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    // ================================================================= shaders

    private static final String VS_QUAD =
            "attribute vec2 aPos;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ vUV = aPos*0.5+0.5; gl_Position = vec4(aPos,0.0,1.0); }\n";

    private static final String VS_POINT =
            "attribute vec2 aPos;\n" +
            "attribute float aSize;\n" +
            "attribute vec4 aCol;\n" +
            "uniform vec2 uRes;\n" +
            "uniform float uScale;\n" +
            "uniform float uGain;\n" +
            "varying vec4 vCol;\n" +
            "void main(){\n" +
            "  gl_Position = vec4(aPos.x/uRes.x*2.0-1.0, 1.0-aPos.y/uRes.y*2.0, 0.0, 1.0);\n" +
            "  gl_PointSize = max(1.0, aSize*uScale);\n" +
            "  vCol = vec4(aCol.rgb, aCol.a*uGain);\n" +
            "}\n";

    private static final String FS_POINT =
            "precision mediump float;\n" +
            "varying vec4 vCol;\n" +
            "void main(){\n" +
            "  vec2 d = gl_PointCoord*2.0-1.0;\n" +
            "  float r2 = dot(d,d);\n" +
            "  if (r2 > 1.0) discard;\n" +
            "  float core = exp(-r2*10.0);\n" +
            "  float glow = exp(-r2*3.5)*0.45;\n" +
            "  float i = (core+glow)*(1.0-r2)*vCol.a;\n" +
            "  vec3 c = mix(vCol.rgb, vec3(1.0), core*0.6);\n" +
            "  gl_FragColor = vec4(c*i, 1.0);\n" +
            "}\n";

    private static final String FS_FADE =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform float uDecay;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 c = texture2D(uTex, vUV).rgb*uDecay - vec3(2.0/255.0);\n" +
            "  gl_FragColor = vec4(max(c, vec3(0.0)), 1.0);\n" +
            "}\n";

    private static final String FS_COPY =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ gl_FragColor = vec4(texture2D(uTex, vUV).rgb, 1.0); }\n";

    private static final String FS_DOWN =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uTexel;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 c = texture2D(uTex, vUV+vec2(-1.0,-1.0)*uTexel).rgb\n" +
            "         + texture2D(uTex, vUV+vec2( 1.0,-1.0)*uTexel).rgb\n" +
            "         + texture2D(uTex, vUV+vec2(-1.0, 1.0)*uTexel).rgb\n" +
            "         + texture2D(uTex, vUV+vec2( 1.0, 1.0)*uTexel).rgb;\n" +
            "  gl_FragColor = vec4(c*0.25, 1.0);\n" +
            "}\n";

    private static final String FS_BLUR =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uDir;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 c = texture2D(uTex, vUV).rgb*0.227027;\n" +
            "  c += (texture2D(uTex, vUV+uDir*1.3846).rgb + texture2D(uTex, vUV-uDir*1.3846).rgb)*0.3162162;\n" +
            "  c += (texture2D(uTex, vUV+uDir*3.2308).rgb + texture2D(uTex, vUV-uDir*3.2308).rgb)*0.0702703;\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    private static final String LIGHT_FN =
            "uniform vec4 uL[8];\n" +
            "uniform vec3 uLC[8];\n" +
            "uniform float uAspect;\n" +
            "vec3 lighting(vec2 p){\n" +
            "  vec3 L = vec3(0.0);\n" +
            "  for (int i=0;i<8;i++){\n" +
            "    vec2 d = vec2((p.x-uL[i].x)*uAspect, p.y-uL[i].y);\n" +
            "    L += uLC[i]*uL[i].z/(1.0+dot(d,d)*14.0);\n" +
            "  }\n" +
            "  return L;\n" +
            "}\n";

    // Cielo + agua con reflejos (coordenada y 'yd' crece hacia abajo, como la pantalla)
    private static final String FS_BG =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform sampler2D uSky;\n" +
            "uniform sampler2D uFw;\n" +
            "uniform sampler2D uB1;\n" +
            "uniform sampler2D uB2;\n" +
            "uniform float uWater;\n" +
            "uniform float uTime;\n" +
            "uniform vec2 uRes;\n" +
            "varying vec2 vUV;\n" +
            LIGHT_FN +
            "float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)))*43758.5453); }\n" +
            "void main(){\n" +
            "  float yd = 1.0 - vUV.y;\n" +
            "  vec3 col;\n" +
            "  if (yd < uWater) {\n" +
            "    vec3 sky = texture2D(uSky, vec2(vUV.x, yd)).rgb;\n" +
            "    vec3 L = lighting(vec2(vUV.x, yd));\n" +
            "    col = sky + L*(0.10 + sky*0.6);\n" +
            "  } else {\n" +
            "    float depth = (yd - uWater)/max(0.001, 1.0-uWater);\n" +
            "    float w = 0.35 + depth*1.4;\n" +
            "    float ox = (sin(yd*260.0/(0.25+depth) + uTime*1.9) * 0.0035\n" +
            "              + sin(yd*90.0 - uTime*1.3 + vUV.x*14.0) * 0.0025) * w;\n" +
            "    float oy = sin(vUV.x*60.0 + uTime*1.1 + yd*40.0)*0.004*depth;\n" +
            "    float ym = 2.0*uWater - yd + oy;\n" +
            "    vec2 su = vec2(vUV.x + ox, clamp(ym, 0.0, uWater));\n" +
            "    vec2 fu = vec2(su.x, 1.0 - su.y);\n" +
            "    vec3 sky = texture2D(uSky, su).rgb;\n" +
            "    vec3 fwc = texture2D(uFw, fu).rgb + texture2D(uB1, fu).rgb*0.9 + texture2D(uB2, fu).rgb*0.8;\n" +
            "    vec3 L = lighting(vec2(vUV.x, ym));\n" +
            "    float refl = mix(0.70, 0.30, depth);\n" +
            "    float sparkle = step(0.985, hash(floor(vec2(vUV.x*uRes.x*0.25, yd*uRes.y*0.5)) + floor(uTime*8.0)));\n" +
            "    col = vec3(0.004, 0.009, 0.018)*(1.0-depth*0.5) + (sky + L*0.06 + fwc*0.85)*refl;\n" +
            "    col += fwc*sparkle*0.6*refl;\n" +
            "    col += L*0.02;\n" +
            "  }\n" +
            "  col += (hash(vUV*uRes + uTime) - 0.5)/255.0;\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    // Capa de fuegos sumada al cielo (solo por encima del agua)
    private static final String FS_ADD =
            "precision mediump float;\n" +
            "uniform sampler2D uFw;\n" +
            "uniform sampler2D uB1;\n" +
            "uniform sampler2D uB2;\n" +
            "uniform float uWater;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  float yd = 1.0 - vUV.y;\n" +
            "  if (yd > uWater) { gl_FragColor = vec4(0.0); return; }\n" +
            "  vec3 c = texture2D(uFw, vUV).rgb + texture2D(uB1, vUV).rgb*1.3 + texture2D(uB2, vUV).rgb*1.4;\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    private static final String VS_SMOKE =
            "attribute vec2 aPos;\n" +
            "attribute float aSize;\n" +
            "attribute vec4 aCol;\n" +
            "attribute float aAng;\n" +
            "uniform vec2 uRes;\n" +
            "varying vec4 vCol;\n" +
            "varying vec2 vRot;\n" +
            "void main(){\n" +
            "  gl_Position = vec4(aPos.x/uRes.x*2.0-1.0, 1.0-aPos.y/uRes.y*2.0, 0.0, 1.0);\n" +
            "  gl_PointSize = aSize;\n" +
            "  vCol = aCol;\n" +
            "  vRot = vec2(cos(aAng), sin(aAng));\n" +
            "}\n";

    private static final String FS_SMOKE =
            "precision mediump float;\n" +
            "uniform sampler2D uCloud;\n" +
            "varying vec4 vCol;\n" +
            "varying vec2 vRot;\n" +
            "void main(){\n" +
            "  vec2 p = gl_PointCoord - 0.5;\n" +
            "  p = vec2(p.x*vRot.x - p.y*vRot.y, p.x*vRot.y + p.y*vRot.x) + 0.5;\n" +
            "  float a = texture2D(uCloud, p).r;\n" +
            "  gl_FragColor = vec4(vCol.rgb, vCol.a*a);\n" +
            "}\n";

    // ================================================================= ciclo GL

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        progPoint = program(VS_POINT, FS_POINT);
        progFade = program(VS_QUAD, FS_FADE);
        progCopy = program(VS_QUAD, FS_COPY);
        progDown = program(VS_QUAD, FS_DOWN);
        progBlur = program(VS_QUAD, FS_BLUR);
        progBg = program(VS_QUAD, FS_BG);
        progAdd = program(VS_QUAD, FS_ADD);
        progSmoke = program(VS_SMOKE, FS_SMOKE);
        float[] range = new float[2];
        GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, range, 0);
        maxPoint = Math.max(16f, range[1]);
        texCloud = makeCloudTexture();
        fbosReady = false;
        texSky = 0;
        lastNs = 0;
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        W = Math.max(1, width);
        H = Math.max(1, height);
        float sc = Math.min(1f, 1600f / Math.max(W, H));
        fwW = Math.max(16, (int) (W * sc));
        fwH = Math.max(16, (int) (H * sc));
        b1W = Math.max(4, fwW / 4);
        b1H = Math.max(4, fwH / 4);
        b2W = Math.max(2, fwW / 12);
        b2H = Math.max(2, fwH / 12);
        fw.resize(W, H, density);

        deleteFbos();
        for (int i = 0; i < 2; i++) {
            int[] t = makeFbo(fwW, fwH);
            trailTex[i] = t[0];
            trailFbo[i] = t[1];
            t = makeFbo(b1W, b1H);
            b1Tex[i] = t[0];
            b1Fbo[i] = t[1];
            t = makeFbo(b2W, b2H);
            b2Tex[i] = t[0];
            b2Fbo[i] = t[1];
        }
        int[] st = makeFbo(fwW, fwH);
        sceneTex = st[0];
        sceneFbo = st[1];
        fbosReady = true;

        if (texSky != 0) GLES20.glDeleteTextures(1, new int[]{texSky}, 0);
        Bitmap sky = makeSkyBitmap(fwW, fwH, Fireworks.waterlineFraction(W, H));
        texSky = uploadBitmap(sky);
        sky.recycle();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : (now - lastNs) / 1e9f;
        lastNs = now;
        if (dt > 0.05f) dt = 0.05f;
        if (dt < 0.0001f) dt = 0.0001f;
        clock += dt;

        fw.update(dt);
        if (!fbosReady) return;

        float water = Fireworks.waterlineFraction(W, H);
        float scale = (float) fwW / W;
        int prev = cur;
        cur = 1 - cur;

        // 1) Persistencia de estelas: copia atenuada del frame anterior + rastro tenue
        int n = fw.fillPoints(pts, maxPoint / Math.max(0.01f, scale));
        if (n > 0) {
            ptsBuf.position(0);
            ptsBuf.put(pts, 0, n * 7);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, trailFbo[cur]);
        GLES20.glViewport(0, 0, fwW, fwH);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(progFade);
        bindTex(progFade, "uTex", trailTex[prev], 0);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progFade, "uDecay"), (float) Math.exp(-dt / 0.10f));
        drawQuad(progFade);
        drawPoints(n, scale, 0.25f);

        // 2) Escena: estelas + cabezas brillantes de las estrellas
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFbo);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(progCopy);
        bindTex(progCopy, "uTex", trailTex[cur], 0);
        drawQuad(progCopy);
        drawPoints(n, scale, 1.35f);

        // 3) Bloom en dos niveles
        bloomLevel(sceneTex, fwW, fwH, b1Fbo, b1Tex, b1W, b1H);
        bloomLevel(b1Tex[0], b1W, b1H, b2Fbo, b2Tex, b2W, b2H);

        // 4) Pantalla: cielo + agua con reflejos
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, W, H);
        GLES20.glDisable(GLES20.GL_BLEND);
        fw.fillLights(lightPos, lightCol);
        GLES20.glUseProgram(progBg);
        bindTex(progBg, "uSky", texSky, 0);
        bindTex(progBg, "uFw", sceneTex, 1);
        bindTex(progBg, "uB1", b1Tex[0], 2);
        bindTex(progBg, "uB2", b2Tex[0], 3);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progBg, "uWater"), water);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progBg, "uTime"), clock % 1000f);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(progBg, "uRes"), W, H);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progBg, "uAspect"), (float) W / H);
        GLES20.glUniform4fv(GLES20.glGetUniformLocation(progBg, "uL"), 8, lightPos, 0);
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(progBg, "uLC"), 8, lightCol, 0);
        drawQuad(progBg);

        // 5) Humo (mezcla alfa normal)
        int sn = fw.fillSmoke(smoke, maxPoint);
        if (sn > 0) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(progSmoke);
            bindTex(progSmoke, "uCloud", texCloud, 0);
            GLES20.glUniform2f(GLES20.glGetUniformLocation(progSmoke, "uRes"), W, H);
            smokeBuf.position(0);
            smokeBuf.put(smoke, 0, sn * 8);
            int stride = 8 * 4;
            int aPos = GLES20.glGetAttribLocation(progSmoke, "aPos");
            int aSize = GLES20.glGetAttribLocation(progSmoke, "aSize");
            int aCol = GLES20.glGetAttribLocation(progSmoke, "aCol");
            int aAng = GLES20.glGetAttribLocation(progSmoke, "aAng");
            smokeBuf.position(0);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, smokeBuf);
            GLES20.glEnableVertexAttribArray(aPos);
            smokeBuf.position(2);
            GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, stride, smokeBuf);
            GLES20.glEnableVertexAttribArray(aSize);
            smokeBuf.position(3);
            GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, stride, smokeBuf);
            GLES20.glEnableVertexAttribArray(aCol);
            smokeBuf.position(7);
            GLES20.glVertexAttribPointer(aAng, 1, GLES20.GL_FLOAT, false, stride, smokeBuf);
            GLES20.glEnableVertexAttribArray(aAng);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, sn);
            GLES20.glDisableVertexAttribArray(aPos);
            GLES20.glDisableVertexAttribArray(aSize);
            GLES20.glDisableVertexAttribArray(aCol);
            GLES20.glDisableVertexAttribArray(aAng);
        }

        // 6) Fuegos + bloom encima (aditivo)
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
        GLES20.glUseProgram(progAdd);
        bindTex(progAdd, "uFw", sceneTex, 0);
        bindTex(progAdd, "uB1", b1Tex[0], 1);
        bindTex(progAdd, "uB2", b2Tex[0], 2);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progAdd, "uWater"), water);
        drawQuad(progAdd);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void drawPoints(int n, float scale, float gain) {
        if (n <= 0) return;
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
        GLES20.glUseProgram(progPoint);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(progPoint, "uRes"), W, H);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progPoint, "uScale"), scale);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(progPoint, "uGain"), gain);
        int stride = 7 * 4;
        int aPos = GLES20.glGetAttribLocation(progPoint, "aPos");
        int aSize = GLES20.glGetAttribLocation(progPoint, "aSize");
        int aCol = GLES20.glGetAttribLocation(progPoint, "aCol");
        ptsBuf.position(0);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, ptsBuf);
        GLES20.glEnableVertexAttribArray(aPos);
        ptsBuf.position(2);
        GLES20.glVertexAttribPointer(aSize, 1, GLES20.GL_FLOAT, false, stride, ptsBuf);
        GLES20.glEnableVertexAttribArray(aSize);
        ptsBuf.position(3);
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, stride, ptsBuf);
        GLES20.glEnableVertexAttribArray(aCol);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, n);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aSize);
        GLES20.glDisableVertexAttribArray(aCol);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /** Reduce src a (w,h) en fbo[0] y aplica desenfoque gaussiano separable (H en fbo[1], V en fbo[0]). */
    private void bloomLevel(int srcTex, int srcW, int srcH, int[] fbo, int[] tex, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glUseProgram(progDown);
        bindTex(progDown, "uTex", srcTex, 0);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(progDown, "uTexel"), 1f / srcW, 1f / srcH);
        drawQuad(progDown);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[1]);
        GLES20.glUseProgram(progBlur);
        bindTex(progBlur, "uTex", tex[0], 0);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(progBlur, "uDir"), 1f / w, 0f);
        drawQuad(progBlur);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        bindTex(progBlur, "uTex", tex[1], 0);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(progBlur, "uDir"), 0f, 1f / h);
        drawQuad(progBlur);
    }

    // ================================================================= utilidades GL

    private void drawQuad(int prog) {
        int aPos = GLES20.glGetAttribLocation(prog, "aPos");
        quad.position(0);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
    }

    private static void bindTex(int prog, String name, int tex, int unit) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, name), unit);
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "Shader error: " + GLES20.glGetShaderInfoLog(s) + "\n" + src);
        }
        return s;
    }

    private static int program(String vs, String fs) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs));
        GLES20.glBindAttribLocation(p, 0, "aPos");
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) Log.e(TAG, "Link error: " + GLES20.glGetProgramInfoLog(p));
        return p;
    }

    private static void texParams(boolean linear) {
        int f = linear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, f);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, f);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private static int[] makeFbo(int w, int h) {
        int[] t = new int[1], f = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        texParams(true);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glGenFramebuffers(1, f, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, f[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, t[0], 0);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return new int[]{t[0], f[0]};
    }

    private void deleteFbos() {
        if (!fbosReady) return;
        GLES20.glDeleteTextures(2, trailTex, 0);
        GLES20.glDeleteTextures(2, b1Tex, 0);
        GLES20.glDeleteTextures(2, b2Tex, 0);
        GLES20.glDeleteFramebuffers(2, trailFbo, 0);
        GLES20.glDeleteFramebuffers(2, b1Fbo, 0);
        GLES20.glDeleteFramebuffers(2, b2Fbo, 0);
        GLES20.glDeleteTextures(1, new int[]{sceneTex}, 0);
        GLES20.glDeleteFramebuffers(1, new int[]{sceneFbo}, 0);
        fbosReady = false;
    }

    private static int uploadBitmap(Bitmap bmp) {
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        texParams(true);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        return t[0];
    }

    /** Textura de nube (ruido fractal con caída radial) para el humo. */
    private static int makeCloudTexture() {
        int n = 128;
        Random rnd = new Random(7);
        int g = 16;
        float[] grid = new float[(g + 1) * (g + 1)];
        for (int i = 0; i < grid.length; i++) grid[i] = rnd.nextFloat();
        ByteBuffer buf = ByteBuffer.allocateDirect(n * n).order(ByteOrder.nativeOrder());
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float u = (float) x / n, v = (float) y / n;
                float val = 0, amp = 0.5f, freq = 2f;
                for (int o = 0; o < 4; o++) {
                    val += amp * valueNoise(grid, g, u * freq, v * freq);
                    amp *= 0.5f;
                    freq *= 2f;
                }
                float dx = u - 0.5f, dy = v - 0.5f;
                float r = (float) Math.sqrt(dx * dx + dy * dy) * 2f;
                float fall = Math.max(0f, 1f - r);
                fall = fall * fall * (3 - 2 * fall);
                float a = fall * (0.35f + val * 0.95f);
                a = Math.max(0f, Math.min(1f, a));
                buf.put((byte) (int) (a * 255));
            }
        }
        buf.position(0);
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        texParams(true);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, n, n, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buf);
        return t[0];
    }

    private static float valueNoise(float[] grid, int g, float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        int x0 = ((xi % g) + g) % g, y0 = ((yi % g) + g) % g;
        int x1 = (x0 + 1) % g, y1 = (y0 + 1) % g;
        float a = grid[y0 * (g + 1) + x0], b = grid[y0 * (g + 1) + x1];
        float c = grid[y1 * (g + 1) + x0], d = grid[y1 * (g + 1) + x1];
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    /** Cielo nocturno con estrellas y silueta de ciudad con ventanas iluminadas. */
    static Bitmap makeSkyBitmap(int w, int h, float waterFrac) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float wy = waterFrac * h;
        float S = Math.min(w, h);
        Random rnd = new Random(2024);

        p.setShader(new LinearGradient(0, 0, 0, wy,
                new int[]{0xff010209, 0xff030716, 0xff081027, 0xff15193a, 0xff2a2238},
                new float[]{0f, 0.35f, 0.7f, 0.92f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, wy, p);
        p.setShader(null);

        // Bruma de contaminación lumínica sobre la ciudad
        p.setShader(new RadialGradient(w * 0.5f, wy, Math.max(w, h) * 0.7f,
                new int[]{0x302a1a10, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, wy, p);
        p.setShader(null);

        // Estrellas
        int stars = (int) (w * wy / 1800f);
        for (int i = 0; i < stars; i++) {
            float x = rnd.nextFloat() * w;
            float y = (float) Math.pow(rnd.nextFloat(), 1.4) * wy * 0.85f;
            float fade = 1f - y / (wy * 0.85f);
            float br = (0.25f + 0.75f * (float) Math.pow(rnd.nextFloat(), 3)) * fade;
            int a = (int) (255 * br);
            float t = rnd.nextFloat();
            int col = t < 0.15f ? 0xffd8e4ff : (t < 0.25f ? 0xfffff0d8 : 0xffffffff);
            p.setColor((col & 0x00ffffff) | (a << 24));
            float rad = (0.35f + rnd.nextFloat() * 0.7f) * Math.max(1f, S / 900f);
            c.drawCircle(x, y, rad, p);
        }

        // Ciudad lejana (más clara por la atmósfera)
        drawSkyline(c, p, rnd, w, wy, S, 0.04f, 0.16f, 0xff0e1324, 0.06f, 0.5f);
        // Ciudad cercana
        drawSkyline(c, p, rnd, w, wy, S, 0.02f, 0.11f, 0xff05060b, 0.16f, 1f);

        // Orilla
        p.setColor(0xff020306);
        c.drawRect(0, wy - Math.max(1f, S * 0.004f), w, wy, p);
        // Debajo del agua (el shader lo reemplaza)
        p.setColor(0xff000000);
        c.drawRect(0, wy, w, h, p);
        return bmp;
    }

    private static void drawSkyline(Canvas c, Paint p, Random rnd, int w, float base, float S,
                                    float minH, float maxH, int color, float windowProb, float lightK) {
        float x = -rnd.nextFloat() * 0.03f * S;
        while (x < w) {
            float bw = (0.025f + rnd.nextFloat() * 0.07f) * S;
            float tall = rnd.nextFloat();
            float bh = (minH + (maxH - minH) * tall * tall) * S + 0.01f * S;
            float top = base - bh;
            p.setColor(color);
            c.drawRect(x, top, x + bw, base, p);
            // remates: antena / escalonado
            if (rnd.nextFloat() < 0.25f) {
                float sw = bw * (0.3f + rnd.nextFloat() * 0.3f);
                float sh = bh * (0.08f + rnd.nextFloat() * 0.12f);
                c.drawRect(x + (bw - sw) / 2, top - sh, x + (bw + sw) / 2, top, p);
                top -= sh;
            }
            if (tall > 0.7f && rnd.nextFloat() < 0.6f) {
                float aw = Math.max(1f, S * 0.002f);
                float ah = bh * 0.25f;
                c.drawRect(x + bw / 2 - aw / 2, top - ah, x + bw / 2 + aw / 2, top, p);
                // luz roja de aviación
                p.setColor(Color(0.9f * lightK, 1f, 0.15f, 0.1f));
                c.drawCircle(x + bw / 2, top - ah, Math.max(1f, S * 0.003f), p);
                p.setColor(color);
            }
            // ventanas
            float ws = Math.max(1f, S * 0.0045f);
            float gap = ws * 2.2f;
            for (float wy = base - bh + gap; wy < base - gap; wy += gap) {
                for (float wx = x + gap * 0.7f; wx < x + bw - ws; wx += gap) {
                    if (rnd.nextFloat() < windowProb) {
                        float a = (0.35f + rnd.nextFloat() * 0.65f) * lightK;
                        float t = rnd.nextFloat();
                        if (t < 0.7f) p.setColor(Color(a, 1f, 0.78f, 0.42f));
                        else if (t < 0.9f) p.setColor(Color(a, 0.85f, 0.9f, 1f));
                        else p.setColor(Color(a, 1f, 0.55f, 0.25f));
                        c.drawRect(wx, wy, wx + ws, wy + ws * 1.3f, p);
                    }
                }
            }
            x += bw + (rnd.nextFloat() < 0.3f ? rnd.nextFloat() * 0.02f * S : 0);
        }
    }

    private static int Color(float a, float r, float g, float b) {
        return ((int) (Math.min(1f, a) * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
