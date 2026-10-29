import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Usage: java MakeIcon.java out.png size -- draws the launcher icon (a little phone with a D-pad). */
public class MakeIcon {
    public static void main(String[] args) throws Exception {
        int s = Integer.parseInt(args[1]);
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float u = s / 48f; // design grid: 48 units

        g.setColor(new Color(0x1E2229));
        g.fill(new RoundRectangle2D.Float(2 * u, 2 * u, 44 * u, 44 * u, 12 * u, 12 * u));

        // Screen
        g.setColor(new Color(0x3FA65F));
        g.fill(new RoundRectangle2D.Float(12 * u, 7 * u, 24 * u, 17 * u, 3 * u, 3 * u));
        g.setColor(new Color(0x2C7A45));
        g.fill(new RoundRectangle2D.Float(15 * u, 17 * u, 6 * u, 4 * u, u, u));
        g.fill(new RoundRectangle2D.Float(24 * u, 12 * u, 8 * u, 9 * u, u, u));

        // D-pad and OK button
        g.setColor(new Color(0x3C414C));
        g.setStroke(new BasicStroke(1.2f * u));
        g.draw(new Ellipse2D.Float(10 * u, 28 * u, 13 * u, 13 * u));
        g.setColor(new Color(0xAAB0BC));
        g.fill(new RoundRectangle2D.Float(15.3f * u, 30 * u, 2.4f * u, 9 * u, u, u));
        g.fill(new RoundRectangle2D.Float(12 * u, 33.3f * u, 9 * u, 2.4f * u, u, u));
        g.setColor(new Color(0x3E8E5A));
        g.fill(new Ellipse2D.Float(28 * u, 30 * u, 9 * u, 9 * u));
        g.dispose();

        new File(args[0]).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(args[0]));
    }
}
