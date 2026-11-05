package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.theguuholi.rentcars.images.ImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class CarServiceImagesTest extends CarImageTestSupport {
  private org.springframework.mock.web.MockMultipartFile widePng() throws Exception {
    var bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(3, 1, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    return new org.springframework.mock.web.MockMultipartFile(
        "image", "wide.png", "image/png", bytes.toByteArray());
  }

  @Nested
  class Add {
    @Test
    @DisplayName(
        "Given invalid image content, when uploading through the car service, then reject it before allocating storage or changing the gallery")
    void invalidContent() {
      var file =
          new org.springframework.mock.web.MockMultipartFile(
              "image", "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      var before = objects.count();
      assertThatThrownBy(() -> service.addImage(car, file)).hasMessageContaining("422");
      assertThat(objects.count()).isEqualTo(before);
      assertThat(service.listImages(car)).isEmpty();
    }

    @Test
    @DisplayName(
        "Given competing uploads, when both complete, then retain both gallery entries without changing the car edit version")
    void concurrent() throws Exception {
      var version = cars.findById(car).orElseThrow().getVersion();
      var ready = new CyclicBarrier(2);
      try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
        var a =
            pool.submit(
                () -> {
                  ready.await();
                  return service.addImage(car, png());
                });
        var b =
            pool.submit(
                () -> {
                  ready.await();
                  return service.addImage(car, widePng());
                });
        var first = a.get(10, TimeUnit.SECONDS);
        var second = b.get(10, TimeUnit.SECONDS);
        assertThat(service.listImages(car))
            .extracting(CarImage::getId)
            .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(
                javax.imageio.ImageIO.read(service.readImage(car, first.getId()).getInputStream())
                    .getWidth())
            .isEqualTo(2);
        assertThat(
                javax.imageio.ImageIO.read(service.readImage(car, second.getId()).getInputStream())
                    .getWidth())
            .isEqualTo(3);
      }
      assertThat(cars.findById(car).orElseThrow().getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName(
        "Given a storage failure, when adding a photo, then preserve the gallery and retain the failed allocation for cleanup")
    void failure() throws Exception {
      var saved = service.addImage(car, png());
      var broken = mock(ImageStorage.class);
      doThrow(new IOException("offline")).when(broken).write(any(), any());
      var failing = new CarService(cars, validator, images, objects, broken, tx);
      long before = objects.count();
      var upload = png();
      assertThatThrownBy(() -> failing.addImage(car, upload))
          .isInstanceOf(UncheckedIOException.class);
      assertThat(objects.count()).isEqualTo(before + 1);
      assertThat(service.listImages(car))
          .extracting(CarImage::getId)
          .containsExactly(saved.getId());
    }

    @Test
    @DisplayName("Given a deleted car, when uploading, then return 404 without publishing an image")
    void missing() throws Exception {
      var missing = UUID.randomUUID();
      var upload = png();
      assertThatThrownBy(() -> service.addImage(missing, upload)).hasMessageContaining("404");
    }
  }

  @Nested
  class Read {
    @Test
    @DisplayName(
        "Given a photo belonging to another car, when reading it under this car, then return 404")
    void wrongCar() throws Exception {
      var image = service.addImage(car, png());
      var original = car;
      var imageId = image.getId();
      var missingCar = UUID.randomUUID();
      createCar();
      assertThatThrownBy(() -> service.readImage(car, imageId)).hasMessageContaining("404");
      assertThatThrownBy(() -> service.readImage(missingCar, imageId)).hasMessageContaining("404");
      assertThatThrownBy(() -> service.removeImage(car, imageId)).hasMessageContaining("404");
      assertThat(service.listImages(original)).hasSize(1);
    }
  }

  @Nested
  class Remove {
    @Test
    @DisplayName(
        "Given a photo, when removing it, then hide it immediately and clean only the unreferenced object")
    void removal() throws Exception {
      var removed = service.addImage(car, png());
      var retained = service.addImage(car, png());
      var removedId = removed.getId();
      var retainedId = retained.getId();
      var missingCar = UUID.randomUUID();
      service.removeImage(car, removedId);
      assertThatThrownBy(() -> service.removeImage(car, removedId)).hasMessageContaining("404");
      assertThatThrownBy(() -> service.removeImage(missingCar, retainedId))
          .hasMessageContaining("404");
      db.sql("update image_objects set created_at=now()-interval '2 hours'").update();
      cleanup.clean();
      assertThatThrownBy(() -> storage.read(removedId)).isInstanceOf(IOException.class);
      assertThat(service.readImage(car, retained.getId()).getContentAsByteArray()).isNotEmpty();
      tx.executeWithoutResult(_ -> cars.deleteById(car));
      assertThat(images.findByCarIdOrderByCreatedAtAscIdAsc(car)).isEmpty();
      cleanup.clean();
      assertThatThrownBy(() -> storage.read(retainedId)).isInstanceOf(IOException.class);
    }
  }

  @Nested
  class ListImages {
    @Test
    @DisplayName(
        "Given a car with no photos or an unknown car, when listing, then distinguish an empty gallery from 404")
    void empty() {
      assertThat(service.listImages(car)).isEmpty();
      var missing = UUID.randomUUID();
      assertThatThrownBy(() -> service.listImages(missing)).hasMessageContaining("404");
    }
  }
}
