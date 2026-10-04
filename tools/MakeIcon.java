import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

/** Genera el icono del juego (fuego artificial sobre cielo nocturno). */
public class MakeIcon {
    public static void main(String[] a) throws Exception {
        int n = Integer.parseInt(a[1]);
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float r = n * 0.22f;
        Shape bg = new RoundRectangle2D.Float(0, 0, n, n, r, r);
        g.setClip(bg);
        g.setPaint(new GradientPaint(0, 0, new Color(4, 6, 22), 0, n, new Color(28, 22, 60)));
        g.fill(bg);
        Random rnd = new Random(5);
        for (int i = 0; i < 40; i++) {
            g.setColor(new Color(255, 255, 255, 40 + rnd.nextInt(120)));
            float s = n / 256f * (1 + rnd.nextFloat() * 1.5f);
            g.fill(new Ellipse2D.Float(rnd.nextFloat() * n, rnd.nextFloat() * n * 0.8f, s, s));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        float cx = n * 0.5f, cy = n * 0.44f;
        Color[] cols = {new Color(255, 70, 60), new Color(255, 190, 70), new Color(80, 200, 255), new Color(140, 255, 110), new Color(220, 120, 255)};
        // halo
        g.setPaint(new RadialGradientPaint(cx, cy, n * 0.42f, new float[]{0f, 1f},
                new Color[]{new Color(255, 200, 120, 110), new Color(255, 120, 60, 0)}));
        g.fill(new Ellipse2D.Float(cx - n * 0.42f, cy - n * 0.42f, n * 0.84f, n * 0.84f));
        int rays = 36;
        for (int i = 0; i < rays; i++) {
            double ang = i * Math.PI * 2 / rays;
            float len = n * (0.30f + rnd.nextFloat() * 0.06f);
            Color c = cols[i % cols.length];
            for (int k = 0; k < 14; k++) {
                float t = (k + 1) / 14f;
                float x = cx + (float) Math.cos(ang) * len * t;
                float y = cy + (float) Math.sin(ang) * len * t + t * t * n * 0.04f;
                float s = n / 128f * (0.8f + t * 2.2f);
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (60 + 195 * t)));
                g.fill(new Ellipse2D.Float(x - s / 2, y - s / 2, s, s));
            }
            float x = cx + (float) Math.cos(ang) * len, y = cy + (float) Math.sin(ang) * len + n * 0.04f;
            float s = n / 40f;
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(x - s / 2, y - s / 2, s, s));
        }
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(cx - n * 0.03f, cy - n * 0.03f, n * 0.06f, n * 0.06f));
        // ciudad
        g.setColor(new Color(3, 4, 10));
        float base = n * 0.86f;
        float x = 0;
        while (x < n) {
            float w = n * (0.06f + rnd.nextFloat() * 0.08f);
            float h = n * (0.05f + rnd.nextFloat() * 0.12f);
            g.fill(new Rectangle2D.Float(x, base - h, w + 1, h + 1));
            x += w;
        }
        g.fill(new Rectangle2D.Float(0, base, n, n - base));
        g.dispose();
        ImageIO.write(img, "png", new File(a[0]));
    }
}
