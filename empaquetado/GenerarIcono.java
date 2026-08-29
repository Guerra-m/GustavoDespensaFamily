import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera el .ico del ejecutable a partir del mismo emoji que ya usa el favicon
 * del frontend (public/favicon.svg), para que el programa instalado y la
 * pestania del navegador se vean como la misma cosa.
 *
 * Escribe un ICO con varias medidas, cada una como PNG embebido (soportado por
 * Windows desde Vista). Java no trae escritor de ICO: el contenedor son 6 bytes
 * de cabecera mas 16 por medida, todo en little-endian, y despues los PNG.
 */
public class GenerarIcono {

    static final int[] MEDIDAS = {16, 32, 48, 64, 128, 256};

    // Se descarto usar el emoji del favicon: Java2D no dibuja fuentes de emoji
    // a color (Segoe UI Emoji sale en blanco), asi que la marca se dibuja con
    // formas. Los celestes son los de la navbar de la app.
    static final Color CELESTE_CLARO = new Color(0x4F, 0xC3, 0xF7);
    static final Color CELESTE_OSCURO = new Color(0x02, 0x88, 0xD1);

    static BufferedImage dibujar(int lado) {
        BufferedImage imagen = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = imagen.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int radio = Math.max(3, lado / 5);
        g.setPaint(new GradientPaint(0, 0, CELESTE_CLARO, 0, lado, CELESTE_OSCURO));
        g.fillRoundRect(0, 0, lado, lado, radio, radio);

        // "D" de Despensa, centrada de verdad usando el alto visual del glifo.
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (lado * 0.66)));
        FontMetrics fm = g.getFontMetrics();
        Rectangle limites = fm.getStringBounds("D", g).getBounds();
        int x = (lado - limites.width) / 2 - limites.x;
        int y = (lado - limites.height) / 2 - limites.y;
        g.drawString("D", x, y);

        g.dispose();
        return imagen;
    }

    static void escribirCorto(OutputStream salida, int valor) throws Exception {
        salida.write(valor & 0xFF);
        salida.write((valor >> 8) & 0xFF);
    }

    static void escribirLargo(OutputStream salida, int valor) throws Exception {
        salida.write(valor & 0xFF);
        salida.write((valor >> 8) & 0xFF);
        salida.write((valor >> 16) & 0xFF);
        salida.write((valor >> 24) & 0xFF);
    }

    public static void main(String[] args) throws Exception {
        Path destino = Path.of(args[0]);

        List<byte[]> pngs = new ArrayList<>();
        for (int lado : MEDIDAS) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(dibujar(lado), "png", buffer);
            pngs.add(buffer.toByteArray());
        }

        try (DataOutputStream salida = new DataOutputStream(new FileOutputStream(destino.toFile()))) {
            escribirCorto(salida, 0);              // reservado
            escribirCorto(salida, 1);              // tipo: icono
            escribirCorto(salida, MEDIDAS.length); // cantidad de medidas

            int desplazamiento = 6 + 16 * MEDIDAS.length;
            for (int i = 0; i < MEDIDAS.length; i++) {
                int lado = MEDIDAS[i];
                salida.write(lado >= 256 ? 0 : lado);  // 0 significa 256
                salida.write(lado >= 256 ? 0 : lado);
                salida.write(0);                       // colores de paleta
                salida.write(0);                       // reservado
                escribirCorto(salida, 1);              // planos
                escribirCorto(salida, 32);             // bits por pixel
                escribirLargo(salida, pngs.get(i).length);
                escribirLargo(salida, desplazamiento);
                desplazamiento += pngs.get(i).length;
            }
            for (byte[] png : pngs) {
                salida.write(png);
            }
        }

        System.out.println("icono escrito: " + destino + " (" + Files.size(destino) + " bytes, "
                + MEDIDAS.length + " medidas)");
    }
}
