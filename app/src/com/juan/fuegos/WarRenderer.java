package com.juan.fuegos;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Render 3D del modo Guerra: cielo nocturno con luna, montañas, bahía con reflejos
 * de las explosiones, edificios con ventanas iluminadas que se queman y se derrumban,
 * humo iluminado, fuego y fuegos artificiales con bloom.
 */
public class WarRenderer implements GLSurfaceView.Renderer {

    private final War war;
    private final float density;

    private int W = 1, H = 1, sW = 1, sH = 1, b1W, b1H, b2W, b2H;
    private int progSky, progBld, progGround, progPart, progSmoke, progDown, progBlur, progComp;
    private int texCloud;
    private int sceneTex, sceneFbo, sceneDepth;
    private final int[] b1Tex = new int[2], b1Fbo = new int[2], b2Tex = new int[2], b2Fbo = new int[2];
    private boolean ready = false;
    private float maxPoint = 64f;

    private final float[] proj = new float[16], view = new float[16], vp = new float[16], invVp = new float[16];
    private final float[] tmp4 = new float[4];
    private final float[] lightPos = new float[32], lightCol = new float[24];

    private final FloatBuffer quad;
    private final float[] bVerts = new float[War.BMAX * 20 * 14];
    private final short[] bIdx = new short[War.BMAX * 30];
    private final FloatBuffer bVertBuf;
    private final ShortBuffer bIdxBuf;
    private FloatBuffer gVertBuf;
    private ShortBuffer gIdxBuf;
    private int gIdxCount;
    private final float[] pts = new float[60000 * 8];
    private final FloatBuffer ptsBuf;
    private final float[] smoke = new float[War.SMAX * 9];
    private final FloatBuffer smokeBuf;

    private long lastNs = 0;
    private float clock = 0;

    private static final float[] MOON_DIR = norm(-0.45f, 0.32f, -1f);

    public WarRenderer(War war, float density) {
        this.war = war;
        this.density = density;
        quad = fb(8);
        quad.put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
        bVertBuf = fb(bVerts.length);
        bIdxBuf = ByteBuffer.allocateDirect(bIdx.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        ptsBuf = fb(pts.length);
        smokeBuf = fb(smoke.length);
        buildGround();
    }

    private static FloatBuffer fb(int n) {
        return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    private static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    // ================================================================= shaders

    private static final String HP =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n";

    private static final String VS_QUAD =
            "attribute vec2 aPos;\n" +
            "varying vec2 vUV;\n" +
            "void main(){ vUV = aPos*0.5+0.5; gl_Position = vec4(aPos,0.0,1.0); }\n";

    private static final String VS_MESH =
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNrm;\n" +
            "attribute vec2 aUV;\n" +
            "attribute vec3 aExt;\n" +
            "attribute vec3 aCol;\n" +
            "uniform mat4 uVP;\n" +
            "varying vec3 vP;\n" +
            "varying vec3 vN;\n" +
            "varying vec2 vUV;\n" +
            "varying vec3 vExt;\n" +
            "varying vec3 vCol;\n" +
            "void main(){\n" +
            "  vP = aPos; vN = aNrm; vUV = aUV; vExt = aExt; vCol = aCol;\n" +
            "  gl_Position = uVP*vec4(aPos,1.0);\n" +
            "}\n";

    private static final String LIGHTS =
            "uniform vec4 uL[8];\n" +
            "uniform vec3 uLC[8];\n" +
            "uniform vec3 uEye;\n" +
            "uniform vec3 uFog;\n" +
            "uniform float uTime;\n" +
            "varying vec3 vP;\n" +
            "varying vec3 vN;\n" +
            "varying vec2 vUV;\n" +
            "varying vec3 vExt;\n" +
            "varying vec3 vCol;\n" +
            "vec3 pointLights(vec3 p, vec3 n){\n" +
            "  vec3 acc = vec3(0.0);\n" +
            "  for (int i=0;i<8;i++){\n" +
            "    vec3 d = uL[i].xyz - p;\n" +
            "    float d2 = dot(d,d) + 0.01;\n" +
            "    float att = uL[i].w*40.0/(40.0+d2);\n" +
            "    acc += uLC[i]*att*max(dot(n, d*inversesqrt(d2)), 0.0);\n" +
            "  }\n" +
            "  return acc;\n" +
            "}\n" +
            "float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)))*43758.5453); }\n" +
            "vec3 fog(vec3 c){ float d = length(vP-uEye); return mix(c, uFog, 1.0-exp(-d*0.0045)); }\n";

    private static final String FS_BLD = HP + LIGHTS +
            "void main(){\n" +
            "  vec3 n = normalize(vN);\n" +
            "  float ch = vExt.x; float sd = vExt.y; float fr = vExt.z;\n" +
            "  vec3 base = vCol*(1.0 - ch*0.85);\n" +
            "  vec3 amb = vec3(0.05,0.06,0.10)*(0.55+0.45*n.y) + vec3(0.03,0.022,0.015);\n" +
            "  vec3 moon = vec3(0.10,0.12,0.18)*max(dot(n, normalize(vec3(-0.45,0.6,-0.6))), 0.0);\n" +
            "  vec3 lit = pointLights(vP, n);\n" +
            "  float ground = smoothstep(0.0, 4.0, vP.y);\n" +
            "  vec3 col = base*(amb*(0.6+0.4*ground) + moon + lit);\n" +
            "  if (sd >= 0.0 && abs(n.y) < 0.5) {\n" +
            "    vec2 f = fract(vUV);\n" +
            "    vec2 cell = floor(vUV);\n" +
            "    float win = step(0.2,f.x)*step(f.x,0.8)*step(0.22,f.y)*step(f.y,0.78);\n" +
            "    float h = hash(cell + vec2(sd, sd*1.7) + n.xz*31.0);\n" +
            "    float on = step(0.5 + ch*0.6, h);\n" +
            "    vec3 wc = mix(vec3(1.0,0.72,0.38), vec3(0.7,0.82,1.0), step(0.87,h)) * (0.35+0.5*fract(h*17.0));\n" +
            "    float burnW = fr*step(0.3, fract(h*7.13))*(0.7+0.3*sin(uTime*14.0+h*50.0));\n" +
            "    vec3 glass = col*0.35 + vec3(0.006,0.008,0.014) + lit*0.2;\n" +
            "    vec3 wcol = glass + wc*on*(1.0-ch) + vec3(1.0,0.42,0.1)*burnW*1.8;\n" +
            "    col = mix(col, wcol, win);\n" +
            "  }\n" +
            "  col += vec3(1.0,0.35,0.08)*fr*0.10*(0.8+0.2*sin(uTime*9.0+vP.y*2.0));\n" +
            "  gl_FragColor = vec4(fog(col), 1.0);\n" +
            "}\n";

    private static final String FS_GROUND = HP + LIGHTS +
            "uniform vec3 uMoonDir;\n" +
            "void main(){\n" +
            "  float mat = vExt.y;\n" +
            "  vec3 col;\n" +
            "  if (mat > -2.5) {\n" +
            "    vec3 n = vec3(0.0,1.0,0.0);\n" +
            "    float v = 0.8 + 0.4*hash(floor(vP.xz*0.5));\n" +
            "    vec3 alb = vec3(0.16,0.17,0.16)*v;\n" +
            "    float city = (1.0 - smoothstep(30.0, 45.0, abs(vP.x)));\n" +
            "    vec3 glow = vec3(0.05,0.035,0.02)*city;\n" +
            "    col = alb*(vec3(0.05,0.06,0.09) + pointLights(vP, n)) + glow*alb*2.0;\n" +
            "  } else if (mat > -3.5) {\n" +
            "    vec3 V = normalize(uEye - vP);\n" +
            "    float t = uTime;\n" +
            "    vec3 nn = normalize(vec3(sin(vP.x*0.9+t*1.3)*0.05 + sin(vP.z*1.7+vP.x*0.3-t*1.9)*0.04, 1.0,\n" +
            "                         cos(vP.z*0.8+t*1.1)*0.05 + sin(vP.x*2.3-t)*0.03));\n" +
            "    vec3 R = reflect(-V, nn);\n" +
            "    float fres = 0.04 + 0.96*pow(1.0-max(dot(V,nn),0.0), 5.0);\n" +
            "    vec3 sky = mix(vec3(0.07,0.06,0.10), vec3(0.004,0.008,0.025), clamp(R.y*2.5,0.0,1.0));\n" +
            "    vec3 spec = vec3(0.0);\n" +
            "    for (int i=0;i<8;i++){\n" +
            "      vec3 L = normalize(uL[i].xyz - vP);\n" +
            "      float s = max(dot(R,L),0.0);\n" +
            "      spec += uLC[i]*uL[i].w*(pow(s,80.0)*2.5 + pow(s,10.0)*0.12);\n" +
            "    }\n" +
            "    float ms = max(dot(R, uMoonDir), 0.0);\n" +
            "    spec += vec3(0.9,0.88,0.8)*(pow(ms,400.0)*1.5 + pow(ms,30.0)*0.05);\n" +
            "    col = vec3(0.004,0.008,0.016) + sky*fres + spec;\n" +
            "  } else {\n" +
            "    col = vec3(0.014,0.016,0.026) + vCol*0.0 + pointLights(vP, vec3(0.0,0.3,0.95))*0.02;\n" +
            "  }\n" +
            "  gl_FragColor = vec4(fog(col), 1.0);\n" +
            "}\n";

    private static final String FS_SKY = HP +
            "uniform vec2 uRes;\n" +
            "uniform float uHorizon;\n" +
            "uniform vec2 uMoon;\n" +
            "uniform vec3 uFlash;\n" +
            "varying vec2 vUV;\n" +
            "float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898,78.233)))*43758.5453); }\n" +
            "void main(){\n" +
            "  float yd = 1.0 - gl_FragCoord.y/uRes.y;\n" +
            "  float t = clamp((uHorizon - yd)/max(0.05,uHorizon), 0.0, 1.0);\n" +
            "  vec3 col = mix(vec3(0.075,0.062,0.10), vec3(0.004,0.008,0.025), pow(t,0.6));\n" +
            "  vec2 c = floor(gl_FragCoord.xy/2.0);\n" +
            "  float h = hash(c);\n" +
            "  col += vec3(smoothstep(0.996,1.0,h))*t*0.9;\n" +
            "  float d = length(gl_FragCoord.xy - uMoon)/uRes.y;\n" +
            "  col += vec3(0.95,0.93,0.85)*smoothstep(0.024,0.021,d);\n" +
            "  col += vec3(0.25,0.27,0.35)*exp(-d*10.0)*0.25;\n" +
            "  col += uFlash*(0.03 + 0.07*(1.0-t));\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    private static final String VS_PART =
            "attribute vec3 aPos;\n" +
            "attribute float aSize;\n" +
            "attribute vec4 aCol;\n" +
            "uniform mat4 uVP;\n" +
            "uniform float uPS;\n" +
            "uniform float uMaxPt;\n" +
            "varying vec4 vCol;\n" +
            "void main(){\n" +
            "  gl_Position = uVP*vec4(aPos,1.0);\n" +
            "  float ps = aSize*uPS/max(gl_Position.w, 0.1);\n" +
            "  gl_PointSize = clamp(ps, 1.5, uMaxPt);\n" +
            "  vCol = vec4(aCol.rgb, aCol.a*min(1.0, ps/1.5));\n" +
            "}\n";

    private static final String FS_PART =
            "precision mediump float;\n" +
            "varying vec4 vCol;\n" +
            "void main(){\n" +
            "  vec2 d = gl_PointCoord*2.0-1.0;\n" +
            "  float r2 = dot(d,d);\n" +
            "  if (r2 > 1.0) discard;\n" +
            "  float core = exp(-r2*10.0);\n" +
            "  float glow = exp(-r2*3.5)*0.45;\n" +
            "  float i = (core+glow)*(1.0-r2)*vCol.a*1.3;\n" +
            "  vec3 c = mix(vCol.rgb, vec3(1.0), core*0.6);\n" +
            "  gl_FragColor = vec4(c*i, 1.0);\n" +
            "}\n";

    private static final String VS_SMOKE =
            "attribute vec3 aPos;\n" +
            "attribute float aSize;\n" +
            "attribute vec4 aCol;\n" +
            "attribute float aAng;\n" +
            "uniform mat4 uVP;\n" +
            "uniform float uPS;\n" +
            "uniform float uMaxPt;\n" +
            "varying vec4 vCol;\n" +
            "varying vec2 vRot;\n" +
            "void main(){\n" +
            "  gl_Position = uVP*vec4(aPos,1.0);\n" +
            "  gl_PointSize = clamp(aSize*uPS/max(gl_Position.w,0.1), 1.0, uMaxPt);\n" +
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

    private static final String FS_DOWN =
            "precision mediump float;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec2 uTexel;\n" +
            "uniform float uThresh;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 c = texture2D(uTex, vUV+vec2(-1.0,-1.0)*uTexel).rgb\n" +
            "         + texture2D(uTex, vUV+vec2( 1.0,-1.0)*uTexel).rgb\n" +
            "         + texture2D(uTex, vUV+vec2(-1.0, 1.0)*uTexel).rgb\n" +
            "         + texture2D(uTex, vUV+vec2( 1.0, 1.0)*uTexel).rgb;\n" +
            "  c *= 0.25;\n" +
            "  c = max(c - vec3(uThresh), vec3(0.0))/(1.0-uThresh);\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
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

    private static final String FS_COMP =
            "precision mediump float;\n" +
            "uniform sampler2D uScene;\n" +
            "uniform sampler2D uB1;\n" +
            "uniform sampler2D uB2;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 c = texture2D(uScene, vUV).rgb + texture2D(uB1, vUV).rgb*1.1 + texture2D(uB2, vUV).rgb*1.2;\n" +
            "  vec2 q = vUV - 0.5;\n" +
            "  c *= 1.0 - dot(q,q)*0.5;\n" +
            "  gl_FragColor = vec4(c, 1.0);\n" +
            "}\n";

    // ================================================================= geometría fija

    /** Tierra de ambas orillas, la bahía y dos cordilleras al fondo. Mismo formato que los edificios. */
    private void buildGround() {
        float[] v = new float[4000 * 14];
        short[] idx = new short[6000];
        int[] c = {0, 0};
        quadXZ(v, idx, c, -200, 200, War.SHORE_PLAYER, 120, -2f);       // tu orilla
        quadXZ(v, idx, c, -200, 200, War.SHORE_ENEMY, War.SHORE_PLAYER, -3f); // agua
        quadXZ(v, idx, c, -200, 200, -260, War.SHORE_ENEMY, -2f);       // orilla enemiga
        Random r = new Random(9);
        ridge(v, idx, c, r, -170f, 6f, 20f);
        ridge(v, idx, c, r, -240f, 14f, 38f);
        gVertBuf = fb(c[0] * 14);
        gVertBuf.put(v, 0, c[0] * 14).position(0);
        gIdxBuf = ByteBuffer.allocateDirect(c[1] * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        gIdxBuf.put(idx, 0, c[1]).position(0);
        gIdxCount = c[1];
    }

    private static void quadXZ(float[] v, short[] idx, int[] c, float x0, float x1, float z0, float z1, float mat) {
        // subdividido para que las luces puntuales se interpolen bien en la niebla
        int nx = 20, nz = 8;
        int base = c[0];
        for (int j = 0; j <= nz; j++) {
            for (int i = 0; i <= nx; i++) {
                float x = x0 + (x1 - x0) * i / nx, z = z0 + (z1 - z0) * j / nz;
                vtx(v, c[0]++, x, 0f, z, 0, 1, 0, mat);
            }
        }
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                int a = base + j * (nx + 1) + i;
                idx[c[1]++] = (short) a; idx[c[1]++] = (short) (a + 1); idx[c[1]++] = (short) (a + nx + 2);
                idx[c[1]++] = (short) a; idx[c[1]++] = (short) (a + nx + 2); idx[c[1]++] = (short) (a + nx + 1);
            }
        }
    }

    private static void ridge(float[] v, short[] idx, int[] c, Random r, float z, float hMin, float hMax) {
        int n = 40;
        int base = c[0];
        float h = hMin + r.nextFloat() * (hMax - hMin);
        for (int i = 0; i <= n; i++) {
            float x = -360f + 720f * i / n;
            h += (r.nextFloat() - 0.5f) * (hMax - hMin) * 0.5f;
            h = Math.max(hMin, Math.min(hMax, h));
            vtx(v, c[0]++, x, 0f, z, 0, 0, 1, -4f);
            vtx(v, c[0]++, x, h, z, 0, 0, 1, -4f);
        }
        for (int i = 0; i < n; i++) {
            int a = base + i * 2;
            idx[c[1]++] = (short) a; idx[c[1]++] = (short) (a + 2); idx[c[1]++] = (short) (a + 3);
            idx[c[1]++] = (short) a; idx[c[1]++] = (short) (a + 3); idx[c[1]++] = (short) (a + 1);
        }
    }

    private static void vtx(float[] v, int i, float x, float y, float z, float nx, float ny, float nz, float mat) {
        int o = i * 14;
        v[o] = x; v[o + 1] = y; v[o + 2] = z;
        v[o + 3] = nx; v[o + 4] = ny; v[o + 5] = nz;
        v[o + 6] = 0; v[o + 7] = 0;
        v[o + 8] = 0; v[o + 9] = mat; v[o + 10] = 0;
        v[o + 11] = 0; v[o + 12] = 0; v[o + 13] = 0;
    }

    // ================================================================= ciclo GL

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        progSky = FireworksRenderer.program(VS_QUAD, FS_SKY);
        progBld = FireworksRenderer.program(VS_MESH, FS_BLD);
        progGround = FireworksRenderer.program(VS_MESH, FS_GROUND);
        progPart = FireworksRenderer.program(VS_PART, FS_PART);
        progSmoke = FireworksRenderer.program(VS_SMOKE, FS_SMOKE);
        progDown = FireworksRenderer.program(VS_QUAD, FS_DOWN);
        progBlur = FireworksRenderer.program(VS_QUAD, FS_BLUR);
        progComp = FireworksRenderer.program(VS_QUAD, FS_COMP);
        float[] range = new float[2];
        GLES20.glGetFloatv(GLES20.GL_ALIASED_POINT_SIZE_RANGE, range, 0);
        maxPoint = Math.max(16f, range[1]);
        texCloud = FireworksRenderer.makeCloudTexture();
        ready = false;
        lastNs = 0;
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        W = Math.max(1, width);
        H = Math.max(1, height);
        float sc = Math.min(1f, 1600f / Math.max(W, H));
        sW = Math.max(16, (int) (W * sc));
        sH = Math.max(16, (int) (H * sc));
        b1W = Math.max(4, sW / 4);
        b1H = Math.max(4, sH / 4);
        b2W = Math.max(2, sW / 12);
        b2H = Math.max(2, sH / 12);

        if (ready) {
            GLES20.glDeleteTextures(1, new int[]{sceneTex}, 0);
            GLES20.glDeleteFramebuffers(1, new int[]{sceneFbo}, 0);
            GLES20.glDeleteRenderbuffers(1, new int[]{sceneDepth}, 0);
            GLES20.glDeleteTextures(2, b1Tex, 0);
            GLES20.glDeleteTextures(2, b2Tex, 0);
            GLES20.glDeleteFramebuffers(2, b1Fbo, 0);
            GLES20.glDeleteFramebuffers(2, b2Fbo, 0);
        }
        int[] t = FireworksRenderer.makeFbo(sW, sH);
        sceneTex = t[0];
        sceneFbo = t[1];
        int[] rb = new int[1];
        GLES20.glGenRenderbuffers(1, rb, 0);
        sceneDepth = rb[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, sceneDepth);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, sW, sH);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFbo);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, sceneDepth);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        for (int i = 0; i < 2; i++) {
            t = FireworksRenderer.makeFbo(b1W, b1H);
            b1Tex[i] = t[0];
            b1Fbo[i] = t[1];
            t = FireworksRenderer.makeFbo(b2W, b2H);
            b2Tex[i] = t[0];
            b2Fbo[i] = t[1];
        }
        ready = true;
        updateMatrices();
    }

    private void updateMatrices() {
        float aspect = (float) W / H;
        // En vertical se abre el campo de visión para que quepan ambas ciudades
        float fovy = aspect >= 1f ? 38f : (float) Math.min(80, Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(30)) / aspect)));
        Matrix.perspectiveM(proj, 0, fovy, aspect, 0.5f, 700f);
        Matrix.setLookAtM(view, 0, war.eye[0], war.eye[1], war.eye[2], war.look[0], war.look[1], war.look[2], 0f, 1f, 0f);
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0);
        Matrix.invertM(invVp, 0, vp, 0);
    }

    /** Llamado desde el hilo GL (queueEvent). */
    public void tap(float x, float y) {
        war.tap(x, y, W, H, vp, invVp, density);
    }

    private float[] screenOf(float x, float y, float z) {
        War.project(vp, x, y, z, tmp4);
        float w = Math.abs(tmp4[3]) < 1e-5f ? 1e-5f : tmp4[3];
        return new float[]{(tmp4[0] / w * 0.5f + 0.5f), (tmp4[1] / w * 0.5f + 0.5f), w};
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : (now - lastNs) / 1e9f;
        lastNs = now;
        dt = Math.max(0.0001f, Math.min(0.05f, dt));
        clock += dt;

        war.update(dt);
        if (!ready) return;
        updateMatrices();
        int nl = war.fillLights(lightPos, lightCol);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneFbo);
        GLES20.glViewport(0, 0, sW, sH);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // --- Cielo
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(progSky);
        float[] hz = screenOf(war.eye[0], 0f, war.eye[2] - 2000f);
        float[] moon = screenOf(war.eye[0] + MOON_DIR[0] * 500f, war.eye[1] + MOON_DIR[1] * 500f,
                war.eye[2] + MOON_DIR[2] * 500f);
        float fr = 0, fg = 0, fbb = 0;
        for (int k = 0; k < nl; k++) {
            fr += lightCol[k * 3] * lightPos[k * 4 + 3];
            fg += lightCol[k * 3 + 1] * lightPos[k * 4 + 3];
            fbb += lightCol[k * 3 + 2] * lightPos[k * 4 + 3];
        }
        GLES20.glUniform2f(loc(progSky, "uRes"), sW, sH);
        GLES20.glUniform1f(loc(progSky, "uHorizon"), 1f - hz[1]);
        GLES20.glUniform2f(loc(progSky, "uMoon"), moon[0] * sW, moon[1] * sH);
        GLES20.glUniform3f(loc(progSky, "uFlash"), fr, fg, fbb);
        drawQuad(progSky);

        // --- Mundo opaco
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDepthMask(true);
        setMeshUniforms(progGround);
        GLES20.glUniform3f(loc(progGround, "uMoonDir"), MOON_DIR[0], MOON_DIR[1], MOON_DIR[2]);
        drawMesh(progGround, gVertBuf, gIdxBuf, gIdxCount);

        int ni = war.fillBuildings(bVerts, bIdx);
        int nv = ni / 30 * 20;
        bVertBuf.position(0);
        bVertBuf.put(bVerts, 0, nv * 14).position(0);
        bIdxBuf.position(0);
        bIdxBuf.put(bIdx, 0, ni).position(0);
        setMeshUniforms(progBld);
        drawMesh(progBld, bVertBuf, bIdxBuf, ni);

        // --- Humo (alfa, sin escribir profundidad)
        GLES20.glDepthMask(false);
        float ps = proj[5] * sH * 0.5f;
        int sn = war.fillSmoke(smoke, lightPos, lightCol, nl);
        if (sn > 0) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glUseProgram(progSmoke);
            FireworksRenderer.bindTex(progSmoke, "uCloud", texCloud, 0);
            GLES20.glUniformMatrix4fv(loc(progSmoke, "uVP"), 1, false, vp, 0);
            GLES20.glUniform1f(loc(progSmoke, "uPS"), ps);
            GLES20.glUniform1f(loc(progSmoke, "uMaxPt"), maxPoint);
            smokeBuf.position(0);
            smokeBuf.put(smoke, 0, sn * 9);
            attr(progSmoke, "aPos", smokeBuf, 0, 3, 9);
            attr(progSmoke, "aSize", smokeBuf, 3, 1, 9);
            attr(progSmoke, "aCol", smokeBuf, 4, 4, 9);
            attr(progSmoke, "aAng", smokeBuf, 8, 1, 9);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, sn);
            disable(progSmoke, "aPos", "aSize", "aCol", "aAng");
        }

        // --- Fuegos artificiales, llamas y chispas (aditivo)
        int n = war.fillPoints(pts);
        if (n > 0) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
            GLES20.glUseProgram(progPart);
            GLES20.glUniformMatrix4fv(loc(progPart, "uVP"), 1, false, vp, 0);
            GLES20.glUniform1f(loc(progPart, "uPS"), ps);
            GLES20.glUniform1f(loc(progPart, "uMaxPt"), maxPoint);
            ptsBuf.position(0);
            ptsBuf.put(pts, 0, n * 8);
            attr(progPart, "aPos", ptsBuf, 0, 3, 8);
            attr(progPart, "aSize", ptsBuf, 3, 1, 8);
            attr(progPart, "aCol", ptsBuf, 4, 4, 8);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, n);
            disable(progPart, "aPos", "aSize", "aCol");
        }
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        // --- Bloom
        bloomLevel(sceneTex, sW, sH, b1Fbo, b1Tex, b1W, b1H, 0.55f);
        bloomLevel(b1Tex[0], b1W, b1H, b2Fbo, b2Tex, b2W, b2H, 0f);

        // --- Pantalla
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, W, H);
        GLES20.glUseProgram(progComp);
        FireworksRenderer.bindTex(progComp, "uScene", sceneTex, 0);
        FireworksRenderer.bindTex(progComp, "uB1", b1Tex[0], 1);
        FireworksRenderer.bindTex(progComp, "uB2", b2Tex[0], 2);
        drawQuad(progComp);
    }

    private void setMeshUniforms(int prog) {
        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(loc(prog, "uVP"), 1, false, vp, 0);
        GLES20.glUniform4fv(loc(prog, "uL"), 8, lightPos, 0);
        GLES20.glUniform3fv(loc(prog, "uLC"), 8, lightCol, 0);
        GLES20.glUniform3f(loc(prog, "uEye"), war.eye[0], war.eye[1], war.eye[2]);
        GLES20.glUniform3f(loc(prog, "uFog"), 0.03f, 0.03f, 0.055f);
        GLES20.glUniform1f(loc(prog, "uTime"), clock % 600f);
    }

    private void drawMesh(int prog, FloatBuffer vb, ShortBuffer ib, int count) {
        if (count <= 0) return;
        attr(prog, "aPos", vb, 0, 3, 14);
        attr(prog, "aNrm", vb, 3, 3, 14);
        attr(prog, "aUV", vb, 6, 2, 14);
        attr(prog, "aExt", vb, 8, 3, 14);
        attr(prog, "aCol", vb, 11, 3, 14);
        ib.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, ib);
        disable(prog, "aPos", "aNrm", "aUV", "aExt", "aCol");
    }

    private static void attr(int prog, String name, FloatBuffer buf, int offset, int size, int stride) {
        int a = GLES20.glGetAttribLocation(prog, name);
        if (a < 0) return;
        buf.position(offset);
        GLES20.glVertexAttribPointer(a, size, GLES20.GL_FLOAT, false, stride * 4, buf);
        GLES20.glEnableVertexAttribArray(a);
    }

    private static void disable(int prog, String... names) {
        for (String n : names) {
            int a = GLES20.glGetAttribLocation(prog, n);
            if (a >= 0) GLES20.glDisableVertexAttribArray(a);
        }
    }

    private static int loc(int prog, String name) {
        return GLES20.glGetUniformLocation(prog, name);
    }

    private void drawQuad(int prog) {
        int aPos = GLES20.glGetAttribLocation(prog, "aPos");
        quad.position(0);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
    }

    private void bloomLevel(int srcTex, int srcW, int srcH, int[] fbo, int[] tex, int w, int h, float thresh) {
        GLES20.glViewport(0, 0, w, h);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        GLES20.glUseProgram(progDown);
        FireworksRenderer.bindTex(progDown, "uTex", srcTex, 0);
        GLES20.glUniform2f(loc(progDown, "uTexel"), 1f / srcW, 1f / srcH);
        GLES20.glUniform1f(loc(progDown, "uThresh"), thresh);
        drawQuad(progDown);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[1]);
        GLES20.glUseProgram(progBlur);
        FireworksRenderer.bindTex(progBlur, "uTex", tex[0], 0);
        GLES20.glUniform2f(loc(progBlur, "uDir"), 1f / w, 0f);
        drawQuad(progBlur);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0]);
        FireworksRenderer.bindTex(progBlur, "uTex", tex[1], 0);
        GLES20.glUniform2f(loc(progBlur, "uDir"), 0f, 1f / h);
        drawQuad(progBlur);
    }
}
