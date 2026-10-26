import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/** Deterministic, transparent pixel-art icons for the ten Crown Forge weapons. */
public final class GenerateCrownWeaponTextures {
    private record Weapon(String id, Color light, Color blade, Color dark, Color gem) {}

    private static final Weapon[] WEAPONS = {
            new Weapon("celestial_edge", new Color(196, 246, 255), new Color(41, 160, 226), new Color(16, 52, 132), new Color(255, 224, 110)),
            new Weapon("apocalypse_blade", new Color(255, 196, 91), new Color(223, 62, 39), new Color(101, 20, 30), new Color(255, 88, 47)),
            new Weapon("void_reaper", new Color(229, 161, 255), new Color(127, 52, 189), new Color(38, 20, 76), new Color(190, 87, 255)),
            new Weapon("thunder_fang", new Color(249, 255, 255), new Color(95, 210, 247), new Color(33, 83, 166), new Color(255, 255, 192)),
            new Weapon("frostmaw", new Color(251, 255, 255), new Color(115, 223, 245), new Color(35, 103, 174), new Color(190, 252, 255)),
            new Weapon("solar_brand", new Color(255, 253, 174), new Color(255, 163, 43), new Color(173, 54, 24), new Color(255, 231, 99)),
            new Weapon("shadow_scythe", new Color(232, 191, 255), new Color(118, 58, 164), new Color(30, 15, 52), new Color(180, 91, 225)),
            new Weapon("tectonic_breaker", new Color(216, 255, 142), new Color(106, 176, 79), new Color(43, 79, 48), new Color(206, 155, 85)),
            new Weapon("gale_dancer", new Color(255, 255, 255), new Color(151, 222, 241), new Color(54, 122, 170), new Color(229, 255, 255)),
            new Weapon("chrono_blade", new Color(255, 255, 218), new Color(190, 199, 222), new Color(90, 106, 152), new Color(255, 216, 115))
    };

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create " + directory);
        for (Weapon weapon : WEAPONS) draw(weapon, new File(directory, weapon.id + ".png"));
    }

    private static void draw(Weapon weapon, File output) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        graphics.setColor(new Color(5, 7, 19));
        graphics.fillPolygon(poly(23, 1, 30, 7, 15, 22, 9, 16));
        graphics.setColor(weapon.blade);
        graphics.fillPolygon(poly(23, 3, 27, 7, 15, 19, 11, 15));
        graphics.setColor(weapon.light);
        graphics.drawLine(24, 4, 13, 15);
        graphics.drawLine(26, 6, 22, 10);
        graphics.setColor(weapon.dark);
        graphics.drawLine(14, 18, 10, 14);

        graphics.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.setColor(new Color(39, 22, 28));
        graphics.drawLine(8, 14, 19, 25);
        graphics.setColor(new Color(239, 193, 91));
        graphics.drawLine(8, 14, 18, 24);
        graphics.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        graphics.setColor(weapon.dark);
        graphics.drawLine(4, 25, 10, 31);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(new Color(238, 202, 128));
        graphics.drawLine(4, 25, 8, 29);

        graphics.setColor(weapon.gem);
        graphics.fillRect(11, 20, 3, 3);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(11, 20, 1, 1);
        graphics.dispose();
        ImageIO.write(image, "png", output);
    }

    private static Polygon poly(int... points) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < points.length; i += 2) polygon.addPoint(points[i], points[i + 1]);
        return polygon;
    }
}
