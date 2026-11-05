package com.github.theguuholi.rentcars.cars;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
class CarImageValidator {
  static final int MAX_BYTES = 5 * 1024 * 1024;

  record Image(byte[] bytes, String contentType, String extension) {}

  Image validate(MultipartFile file) {
    if (file.getSize() > MAX_BYTES)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds 5 MB");
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES)
        throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image exceeds 5 MB");
      try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw invalid();
        var reader = readers.next();
        try {
          String format = reader.getFormatName();
          if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw invalid();
          reader.setInput(stream);
          // Bound decompression before decoding an otherwise tiny compressed upload.
          if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw invalid();
          reader.read(0);
          boolean jpeg = format.equalsIgnoreCase("JPEG");
          return new Image(bytes, jpeg ? "image/jpeg" : "image/png", jpeg ? "jpg" : "png");
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException exception) {
      throw invalid();
    }
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Provide a valid JPEG or PNG image with at most 20 million pixels");
  }
}
