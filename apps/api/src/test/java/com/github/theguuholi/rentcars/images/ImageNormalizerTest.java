package com.github.theguuholi.rentcars.images;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class ImageNormalizerTest {
  static byte[] pixels(int w, int h, String format) throws IOException {
    var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    var out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  @Nested
  class Normalize {
    @Test
    @DisplayName(
        "Given a rendering failure, when normalization aborts, then release both the graphics context and image reader")
    void resourcesAreReleased() throws Exception {
      var reader = mock(javax.imageio.ImageReader.class);
      var decoded = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
      when(reader.getFormatName()).thenReturn("png");
      when(reader.getWidth(0)).thenReturn(1);
      when(reader.getHeight(0)).thenReturn(1);
      when(reader.read(0)).thenReturn(decoded);
      var graphics = mock(java.awt.Graphics2D.class);
      var failure = new IllegalStateException("rendering failed");
      when(graphics.drawImage(
              any(java.awt.Image.class), anyInt(), anyInt(), anyInt(), anyInt(), isNull()))
          .thenThrow(failure);
      var file = new MockMultipartFile("image", new byte[] {1});
      try (var images = mockStatic(ImageIO.class);
          var buffers =
              mockConstruction(
                  BufferedImage.class,
                  (buffer, _) -> when(buffer.createGraphics()).thenReturn(graphics))) {
        images
            .when(() -> ImageIO.getImageReaders(any()))
            .thenReturn(java.util.List.of(reader).iterator());
        assertThatThrownBy(() -> ImageNormalizer.normalize(file, 512)).isSameAs(failure);
        assertThat(buffers.constructed()).hasSize(1);
        verify(graphics).dispose();
        verify(reader).dispose();
      }
    }

    @Test
    @DisplayName(
        "Given a high resolution car photo, when normalizing for the gallery, then preserve aspect ratio within 1600 pixels")
    void carPhoto() throws Exception {
      var file = new MockMultipartFile("image", pixels(2000, 1000, "jpeg"));
      var png = ImageNormalizer.normalize(file, 1600);
      var image = ImageIO.read(new ByteArrayInputStream(png));
      assertThat(image.getWidth()).isEqualTo(1600);
      assertThat(image.getHeight()).isEqualTo(800);
    }

    @ParameterizedTest(name = "Given {2} {0}x{1}, when normalizing, then produce a bounded PNG")
    @CsvSource({"512,512,png", "1024,512,jpeg", "1,1000,png", "2000,2000,png"})
    void normal(int w, int h, String format) throws Exception {
      byte[] source = pixels(w, h, format);
      var normalized =
          ImageNormalizer.normalize(
              new MockMultipartFile("image", "../../untrusted.svg", "text/html", source), 512);
      assertThat(normalized).startsWith((byte) 137, (byte) 80, (byte) 78, (byte) 71);
      var decoded = ImageIO.read(new ByteArrayInputStream(normalized));
      double ratio = Math.min(1, 512d / Math.max(w, h));
      assertThat(decoded.getWidth()).isEqualTo(Math.max(1, (int) (w * ratio)));
      assertThat(decoded.getHeight()).isEqualTo(Math.max(1, (int) (h * ratio)));
    }

    @Test
    @DisplayName(
        "Given a valid PNG exactly at the byte limit, when normalizing, then accept it and remove trailing bytes")
    void exactLimit() throws Exception {
      var bytes = java.util.Arrays.copyOf(pixels(1, 1, "png"), 5 * 1024 * 1024);
      var result = ImageNormalizer.normalize(new MockMultipartFile("image", bytes), 512);
      assertThat(result).hasSizeLessThan(1024);
      assertThat(ImageIO.read(new ByteArrayInputStream(result)).getWidth()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "Given a renamed executable or an empty image, when decoding, then reject the content")
    void fake() {
      for (var value : new byte[][] {new byte[0], "<svg onload='alert(1)'/>".getBytes()}) {
        var image = new MockMultipartFile("image", "valid.jpg", "image/jpeg", value);
        assertThatThrownBy(() -> ImageNormalizer.normalize(image, 512))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
      }
    }

    @Test
    @DisplayName("Given an oversized file, when validating, then reject it before decoding")
    void bytes() {
      var file = new MockMultipartFile("image", new byte[5 * 1024 * 1024 + 1]);
      assertThatThrownBy(() -> ImageNormalizer.normalize(file, 512))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("413");
    }

    @Test
    @DisplayName(
        "Given an image over four megapixels, when reading dimensions, then reject before decoding")
    void dimensions() throws Exception {
      var file = new MockMultipartFile("image", pixels(2001, 2000, "png"));
      assertThatThrownBy(() -> ImageNormalizer.normalize(file, 512)).hasMessageContaining("422");
    }

    @Test
    @DisplayName("Given a GIF or corrupt PNG, when inspecting the image, then reject it")
    void unsupported() throws Exception {
      var gif = new MockMultipartFile("image", pixels(2, 2, "gif"));
      assertThatThrownBy(() -> ImageNormalizer.normalize(gif, 512)).hasMessageContaining("422");
      var corrupt =
          new MockMultipartFile("image", new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
      assertThatThrownBy(() -> ImageNormalizer.normalize(corrupt, 512)).hasMessageContaining("422");
    }
  }
}
