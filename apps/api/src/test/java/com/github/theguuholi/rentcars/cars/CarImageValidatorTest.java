package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class CarImageValidatorTest {
  CarImageValidator validator = new CarImageValidator();

  byte[] image(String format) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, bytes);
    return bytes.toByteArray();
  }

  void invalid(byte[] bytes) {
    assertThatThrownBy(() -> validator.validate(new MockMultipartFile("images", bytes)))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
  }

  @Nested
  class Validate {
    @Test
    @DisplayName(
        "Given JPEG or PNG bytes with misleading metadata, when validating, then the actual type and bytes are retained")
    void validFormats() throws Exception {
      for (String format : new String[] {"jpeg", "png"}) {
        var bytes = image(format);
        var valid =
            validator.validate(new MockMultipartFile("images", "x.exe", "text/plain", bytes));
        assertThat(valid.contentType()).isEqualTo("image/" + format);
        assertThat(valid.extension()).isEqualTo(format.equals("jpeg") ? "jpg" : "png");
        assertThat(valid.bytes()).isEqualTo(bytes);
      }
    }

    @Test
    @DisplayName(
        "Given empty, unsupported or truncated images, when validating, then they are rejected")
    void malformed() throws Exception {
      invalid(new byte[0]);
      invalid(new byte[] {1, 2});
      invalid(image("gif"));
      invalid(Arrays.copyOf(image("png"), 40));
    }

    @Test
    @DisplayName(
        "Given an image exceeding the decoded pixel limit, when validating, then it is rejected before decoding")
    void pixelLimit() throws Exception {
      var bytes = image("png");
      ByteBuffer.wrap(bytes).putInt(16, 5000).putInt(20, 5000);
      CRC32 crc = new CRC32();
      crc.update(bytes, 12, 17);
      ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
      invalid(bytes);
    }

    @Test
    @DisplayName(
        "Given oversized content, when validating even with a false declared size, then the bounded reader rejects it")
    void oversized() throws Exception {
      byte[] bytes = new byte[CarImageValidator.MAX_BYTES + 1];
      assertThatThrownBy(() -> validator.validate(new MockMultipartFile("images", bytes)))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(413));
      MultipartFile file = mock();
      when(file.getInputStream()).thenReturn(new ByteArrayInputStream(bytes));
      assertThatThrownBy(() -> validator.validate(file))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(413));
    }

    @Test
    @DisplayName(
        "Given an unreadable multipart stream, when validating, then the API reports invalid input")
    void ioFailure() throws Exception {
      MultipartFile file = mock();
      when(file.getInputStream()).thenThrow(new IOException("unreadable"));
      assertThatThrownBy(() -> validator.validate(file))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode().value()).isEqualTo(422));
    }
  }
}
