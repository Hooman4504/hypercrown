import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** Tiny deterministic 32px pixel-art texture generator for the catastrophe gems. */
public final class GenerateGemTextures {
    private record Gem(String id, Color light, Color mid, Color dark, String motif) {}

    private static final Gem[] GEMS = {
            new Gem("celestial_sapphire", new Color(93, 210, 255), new Color(24, 83, 202), new Color(10, 18, 73), "cosmos"),
            new Gem("apocalyptic_ruby", new Color(255, 138, 79), new Color(200, 23, 42), new Color(74, 6, 25), "ruby"),
            new Gem("void_emerald", new Color(193, 105, 255), new Color(51, 25, 87), new Color(12, 10, 24), "void"),
            new Gem("thunder_diamond", new Color(222, 255, 255), new Color(55, 190, 232), new Color(26, 73, 161), "lightning"),
            new Gem("frost_topaz", new Color(233, 255, 255), new Color(94, 218, 239), new Color(28, 106, 177), "frost"),
            new Gem("solar_opal", new Color(255, 255, 184), new Color(255, 137, 28), new Color(170, 47, 17), "solar"),
            new Gem("shadow_amethyst", new Color(214, 143, 255), new Color(109, 42, 168), new Color(28, 10, 57), "shadow"),
            new Gem("tectonic_jade", new Color(174, 255, 125), new Color(55, 157, 73), new Color(16, 65, 45), "earth"),
            new Gem("wind_sapphire", new Color(241, 255, 255), new Color(133, 216, 237), new Color(48, 124, 178), "wind"),
            new Gem("time_quartz", new Color(255, 255, 226), new Color(198, 203, 221), new Color(104, 123, 160), "time")
    };

    public static void main(String[] args) throws Exception {
        File folder = new File(args[0]);
        for (Gem gem : GEMS) {
            draw(gem, false, new File(folder, gem.id + ".png"));
            draw(gem, true, new File(folder, "enhanced_" + gem.id + ".png"));
        }
    }

    private static void draw(Gem gem, boolean enhanced, File out) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (enhanced) {
            g.setColor(new Color(255, 194, 67, 75));
            g.fillOval(2, 2, 28, 28);
            g.setColor(new Color(255, 215, 105));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(3, 3, 25, 25);
        }
        g.setColor(new Color(4, 8, 20));
        g.fillPolygon(new int[]{16, 25, 23, 16, 9, 7}, new int[]{2, 10, 22, 30, 22, 10}, 6);
        g.setPaint(new GradientPaint(8, 8, gem.light, 25, 25, gem.dark));
        g.fillPolygon(new int[]{16, 23, 21, 16, 11, 9}, new int[]{4, 11, 21, 27, 21, 11}, 6);
        g.setColor(gem.mid);
        g.fillPolygon(new int[]{16, 21, 16, 11}, new int[]{5, 12, 25, 12}, 4);
        g.setColor(gem.light);
        g.fillPolygon(new int[]{16, 20, 16, 13}, new int[]{5, 11, 15, 11}, 4);
        g.setColor(gem.dark);
        g.fillPolygon(new int[]{16, 21, 16, 16}, new int[]{15, 12, 25, 21}, 4);
        g.setColor(new Color(255, 255, 255, 210));
        g.fillRect(11, 10, 2, 3);
        g.fillRect(15, 6, 2, 2);

        g.setStroke(new BasicStroke(1.0f));
        switch (gem.motif) {
            case "cosmos" -> {
                g.setColor(new Color(136, 227, 255));
                g.draw(new Arc2D.Double(4, 6, 24, 18, 195, 145, Arc2D.OPEN));
                g.fillRect(6, 7, 2, 2); g.fillRect(25, 8, 2, 2); g.fillRect(23, 25, 1, 1);
                g.setColor(new Color(238, 248, 255)); g.fillRect(24, 5, 2, 2);
            }
            case "ruby" -> {
                g.setColor(new Color(255, 92, 60)); g.drawOval(8, 8, 16, 16); g.drawOval(11, 11, 10, 10);
                g.setColor(new Color(255, 207, 103)); g.fillRect(17, 13, 3, 3);
            }
            case "void" -> {
                g.setColor(new Color(181, 86, 255)); g.draw(new Arc2D.Double(5, 5, 23, 22, 35, 285, Arc2D.OPEN));
                g.drawLine(21, 11, 25, 7); g.drawLine(21, 12, 25, 8);
            }
            case "lightning" -> {
                g.setColor(new Color(245, 255, 255)); g.fillPolygon(new int[]{17, 12, 16, 13, 21, 17, 20}, new int[]{7, 16, 16, 24, 16, 16, 11}, 7);
                g.setColor(new Color(115, 243, 255)); g.drawLine(8, 14, 11, 11); g.drawLine(21, 23, 25, 19);
            }
            case "frost" -> {
                g.setColor(new Color(244, 255, 255));
                for (int i = 0; i < 3; i++) { g.drawLine(16, 9 + i * 2, 16, 23 - i * 2); g.drawLine(10 + i * 2, 16, 22 - i * 2, 16); }
                g.drawLine(12, 12, 20, 20); g.drawLine(20, 12, 12, 20);
            }
            case "solar" -> {
                g.setColor(new Color(255, 220, 87));
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; g.drawLine(16 + (int)(Math.cos(a)*10), 16 + (int)(Math.sin(a)*10), 16 + (int)(Math.cos(a)*14), 16 + (int)(Math.sin(a)*14)); }
                g.setColor(new Color(255, 248, 170)); g.fillRect(15, 12, 4, 4);
            }
            case "shadow" -> {
                g.setColor(new Color(215, 124, 255)); g.draw(new Arc2D.Double(5, 8, 22, 17, 205, 255, Arc2D.OPEN));
                g.draw(new Arc2D.Double(10, 4, 17, 23, 20, 150, Arc2D.OPEN)); g.fillRect(7, 20, 2, 2);
            }
            case "earth" -> {
                g.setColor(new Color(188, 255, 133)); g.drawLine(11, 12, 15, 15); g.drawLine(15, 15, 12, 20);
                g.drawLine(19, 11, 17, 15); g.drawLine(17, 15, 21, 19); g.drawLine(14, 22, 18, 24);
            }
            case "wind" -> {
                g.setColor(new Color(243, 255, 255));
                g.draw(new QuadCurve2D.Double(5, 10, 22, 4, 25, 11));
                g.draw(new QuadCurve2D.Double(4, 16, 24, 10, 25, 18));
                g.draw(new QuadCurve2D.Double(7, 22, 19, 18, 24, 23));
            }
            case "time" -> {
                g.setColor(new Color(255, 224, 128)); g.drawOval(10, 9, 12, 12);
                g.drawLine(16, 11, 16, 16); g.drawLine(16, 16, 19, 17);
                g.drawLine(7, 8, 10, 5); g.drawLine(23, 24, 26, 21);
            }
        }
        if (enhanced) {
            g.setColor(new Color(255, 225, 132));
            g.fillRect(4, 14, 2, 2); g.fillRect(26, 15, 2, 2); g.fillRect(15, 2, 2, 2); g.fillRect(15, 28, 2, 2);
        }
        g.dispose();
        ImageIO.write(image, "png", out);
    }
}
