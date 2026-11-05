package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.theguuholi.rentcars.images.ImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class CarImageServiceTest extends CarImageTestSupport {
  @Nested
  class Add {
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
                  return service.add(car, new byte[] {1});
                });
        var b =
            pool.submit(
                () -> {
                  ready.await();
                  return service.add(car, new byte[] {2});
                });
        var first = a.get(10, TimeUnit.SECONDS);
        var second = b.get(10, TimeUnit.SECONDS);
        assertThat(service.list(car))
            .extracting(CarImage::getId)
            .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(service.read(car, first.getId())).containsExactly(1);
        assertThat(service.read(car, second.getId())).containsExactly(2);
      }
      assertThat(cars.findById(car).orElseThrow().getVersion()).isEqualTo(version);
    }

    @Test
    @DisplayName(
        "Given a storage failure, when adding a photo, then preserve the gallery and retain the failed allocation for cleanup")
    void failure() throws Exception {
      var saved = service.add(car, new byte[] {1});
      var broken = mock(ImageStorage.class);
      doThrow(new IOException("offline")).when(broken).write(any(), any());
      var failing = new CarImageService(cars, images, objects, broken, tx);
      long before = objects.count();
      assertThatThrownBy(() -> failing.add(car, new byte[] {2}))
          .isInstanceOf(UncheckedIOException.class);
      assertThat(objects.count()).isEqualTo(before + 1);
      assertThat(service.list(car)).extracting(CarImage::getId).containsExactly(saved.getId());
    }

    @Test
    @DisplayName("Given a deleted car, when uploading, then return 404 without publishing an image")
    void missing() {
      var missing = UUID.randomUUID();
      assertThatThrownBy(() -> service.add(missing, new byte[] {1})).hasMessageContaining("404");
    }
  }

  @Nested
  class Read {
    @Test
    @DisplayName(
        "Given a photo belonging to another car, when reading it under this car, then return 404")
    void wrongCar() {
      var image = service.add(car, new byte[] {1});
      var original = car;
      var imageId = image.getId();
      var missingCar = UUID.randomUUID();
      createCar();
      assertThatThrownBy(() -> service.read(car, imageId)).hasMessageContaining("404");
      assertThatThrownBy(() -> service.read(missingCar, imageId)).hasMessageContaining("404");
      assertThatThrownBy(() -> service.remove(car, imageId)).hasMessageContaining("404");
      assertThat(service.list(original)).hasSize(1);
    }
  }

  @Nested
  class Remove {
    @Test
    @DisplayName(
        "Given a photo, when removing it, then hide it immediately and clean only the unreferenced object")
    void removal() throws Exception {
      var removed = service.add(car, new byte[] {1});
      var retained = service.add(car, new byte[] {2});
      var removedId = removed.getId();
      var retainedId = retained.getId();
      var missingCar = UUID.randomUUID();
      service.remove(car, removedId);
      assertThatThrownBy(() -> service.remove(car, removedId)).hasMessageContaining("404");
      assertThatThrownBy(() -> service.remove(missingCar, retainedId)).hasMessageContaining("404");
      db.sql("update image_objects set created_at=now()-interval '2 hours'").update();
      cleanup.clean();
      assertThatThrownBy(() -> storage.read(removedId)).isInstanceOf(IOException.class);
      assertThat(service.read(car, retained.getId())).containsExactly(2);
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
      assertThat(service.list(car)).isEmpty();
      var missing = UUID.randomUUID();
      assertThatThrownBy(() -> service.list(missing)).hasMessageContaining("404");
    }
  }
}
