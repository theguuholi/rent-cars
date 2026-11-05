package com.github.theguuholi.rentcars;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CarImageMultipartLimitTest {
  @Value("${local.server.port}")
  int port;

  @Autowired JwtEncoder encoder;

  int upload(int count, int size) throws Exception {
    var now = Instant.now();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwtClaimsSet.builder()
                        .issuer("rent-cars")
                        .audience(List.of("rent-cars-api"))
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .claim("roles", List.of("ADMIN"))
                        .build()))
            .getTokenValue();
    var bytes = new ByteArrayOutputStream();
    for (int i = 0; i < count; i++) {
      bytes.write(
          ("--limit\r\nContent-Disposition: form-data; name=\"images\"; filename=\"image.png\"\r\nContent-Type: image/png\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      bytes.write(new byte[size]);
      bytes.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    bytes.write("--limit--\r\n".getBytes(StandardCharsets.UTF_8));
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cars"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "multipart/form-data; boundary=limit")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()))
            .build();
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
  }

  @Nested
  class Upload {
    @Test
    @DisplayName(
        "Given a file over five MiB, when posting to the real servlet container, then it returns 413")
    void fileLimit() throws Exception {
      assertThat(upload(1, 5 * 1024 * 1024 + 1)).isEqualTo(413);
    }

    @Test
    @DisplayName(
        "Given individually valid file sizes exceeding the total request limit, when posting, then the servlet returns 413")
    void requestLimit() throws Exception {
      assertThat(upload(7, 4600 * 1024)).isEqualTo(413);
    }
  }
}
