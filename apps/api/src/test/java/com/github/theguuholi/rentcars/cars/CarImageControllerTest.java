package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;

class CarImageControllerTest extends CarImageTestSupport {
  MockMultipartFile png() throws Exception {
    return new MockMultipartFile(
        "image",
        "photo.png",
        "image/png",
        Files.readAllBytes(Path.of("../../requests/fixtures/avatar.png")));
  }

  @Nested
  class Upload {
    @Test
    @DisplayName(
        "Given an admin and a PNG, when uploading, then return the image entity and public PNG location without exposing storage details")
    void lifecycle() throws Exception {
      var path = "/cars/" + car + "/images";
      var result =
          mvc.perform(multipart(path).file(png()).header("Authorization", token("ADMIN")))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.id").isString())
              .andExpect(jsonPath("$.carId").doesNotExist())
              .andReturn()
              .getResponse();
      var id = json.readTree(result.getContentAsString()).get("id").asString();
      assertThat(result.getHeader("Location")).isEqualTo(path + "/" + id);
      mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
      var bytes =
          mvc.perform(get(path + "/" + id))
              .andExpect(status().isOk())
              .andExpect(content().contentType("image/png"))
              .andExpect(header().string("Cache-Control", "no-store"))
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)).getWidth())
          .isEqualTo(2);
      mvc.perform(delete(path + "/" + id).header("Authorization", token("ADMIN")))
          .andExpect(status().isNoContent());
      mvc.perform(get(path + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
        "Given a guest or regular user, when writing car images, then reject upload and deletion without modifying the gallery")
    void permissions() throws Exception {
      var path = "/cars/" + car + "/images";
      mvc.perform(multipart(path).file(png())).andExpect(status().isUnauthorized());
      mvc.perform(multipart(path).file(png()).header("Authorization", token("USER")))
          .andExpect(status().isForbidden());
      var image = service.add(car, new byte[] {1});
      mvc.perform(delete(path + "/" + image.getId())).andExpect(status().isUnauthorized());
      mvc.perform(delete(path + "/" + image.getId()).header("Authorization", token("USER")))
          .andExpect(status().isForbidden());
      assertThat(service.list(car)).hasSize(1);
    }

    @Test
    @DisplayName(
        "Given invalid or oversized image content, when an admin uploads, then reject it with 422 or 413")
    void invalid() throws Exception {
      var path = "/cars/" + car + "/images";
      var admin = token("ADMIN");
      mvc.perform(
              multipart(path)
                  .file(new MockMultipartFile("image", "<svg/>".getBytes()))
                  .header("Authorization", admin))
          .andExpect(status().isUnprocessableContent());
      mvc.perform(
              multipart(path)
                  .file(new MockMultipartFile("image", new byte[5 * 1024 * 1024 + 1]))
                  .header("Authorization", admin))
          .andExpect(status().isContentTooLarge());
      mvc.perform(multipart(path).header("Authorization", admin))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class ListImages {
    @Test
    @DisplayName("Given a missing car, when a visitor lists its gallery, then return 404")
    void missing() throws Exception {
      mvc.perform(get("/cars/" + UUID.randomUUID() + "/images")).andExpect(status().isNotFound());
    }
  }

  @Nested
  class Documentation {
    @Test
    @DisplayName(
        "Given the gallery endpoints, when reading OpenAPI, then describe the entity, multipart upload and public PNG content")
    void schema() throws Exception {
      var body =
          mvc.perform(get("/v3/api-docs"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      var doc = json.readTree(body);
      assertThat(doc.at("/components/schemas/CarImage/properties/id/readOnly").asBoolean())
          .isTrue();
      assertThat(
              doc.get("paths")
                  .get("/cars/{carId}/images")
                  .get("post")
                  .get("requestBody")
                  .get("content")
                  .has("multipart/form-data"))
          .isTrue();
      assertThat(
              doc.get("paths")
                  .get("/cars/{carId}/images/{imageId}")
                  .get("get")
                  .get("responses")
                  .get("200")
                  .get("content")
                  .has("image/png"))
          .isTrue();
    }
  }
}
