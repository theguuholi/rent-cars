package com.github.theguuholi.rentcars.images;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

public final class ImageNormalizer {
  private ImageNormalizer() {}

  public static byte[] normalize(MultipartFile file, int maxEdge) throws IOException {
    if (file.getSize() > 5L * 1024 * 1024)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    try (var raw = file.getInputStream();
        var stream = new MemoryCacheImageInputStream(raw)) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw invalid();
      try (var resource = new ReaderResource(readers.next())) {
        var reader = resource.reader();
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) throw invalid();
        reader.setInput(stream, true, true);
        int w = reader.getWidth(0);
        int h = reader.getHeight(0);
        if ((long) w * h > 4_000_000L) throw invalid();
        BufferedImage decoded = reader.read(0);
        double scale = Math.min(1d, (double) maxEdge / Math.max(w, h));
        var resized =
            new BufferedImage(
                Math.max(1, (int) (w * scale)),
                Math.max(1, (int) (h * scale)),
                BufferedImage.TYPE_INT_ARGB);
        var graphics = resized.createGraphics();
        try {
          graphics.drawImage(decoded, 0, 0, resized.getWidth(), resized.getHeight(), null);
        } finally {
          graphics.dispose();
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", bytes);
        return bytes.toByteArray();
      } catch (javax.imageio.IIOException _) {
        throw invalid();
      }
    }
  }

  /** ImageReader exposes dispose rather than AutoCloseable; adapt its lifetime to the try block. */
  private record ReaderResource(ImageReader reader) implements AutoCloseable {
    @Override
    public void close() {
      reader.dispose();
    }
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT, "Choose a JPEG or PNG of up to 4 megapixels.");
  }
}
