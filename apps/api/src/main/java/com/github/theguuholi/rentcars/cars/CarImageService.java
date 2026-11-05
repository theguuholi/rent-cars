package com.github.theguuholi.rentcars.cars;

import com.github.theguuholi.rentcars.images.ImageObject;
import com.github.theguuholi.rentcars.images.ImageObjectRepository;
import com.github.theguuholi.rentcars.images.ImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class CarImageService {
  private final CarRepository cars;
  private final CarImageRepository images;
  private final ImageObjectRepository objects;
  private final ImageStorage storage;
  private final TransactionTemplate tx;

  CarImageService(
      CarRepository cars,
      CarImageRepository images,
      ImageObjectRepository objects,
      ImageStorage storage,
      TransactionTemplate tx) {
    this.cars = cars;
    this.images = images;
    this.objects = objects;
    this.storage = storage;
    this.tx = tx;
  }

  @Transactional(readOnly = true)
  List<CarImage> list(UUID car) {
    if (!cars.existsById(car)) throw missing();
    return images.findByCarIdOrderByCreatedAtAscIdAsc(car);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  CarImage add(UUID car, byte[] bytes) {
    var key = UUID.randomUUID();
    objects.saveAndFlush(new ImageObject(key));
    return tx.execute(
        _ -> {
          objects.lockAllocation(key);
          cars.findForUpdate(car).orElseThrow(CarImageService::missing);
          try {
            storage.write(key, bytes);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          return images.saveAndFlush(new CarImage(key, car));
        });
  }

  @Transactional
  byte[] read(UUID car, UUID image) throws IOException {
    // Coordinate reads with deletion of the gallery entry or its parent car.
    cars.findForRead(car).orElseThrow(CarImageService::missing);
    var entry = images.findByIdAndCarId(image, car).orElseThrow(CarImageService::missing);
    return storage.read(entry.getId());
  }

  @Transactional
  void remove(UUID car, UUID image) {
    cars.findForUpdate(car).orElseThrow(CarImageService::missing);
    images.delete(images.findByIdAndCarId(image, car).orElseThrow(CarImageService::missing));
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Car or image not found");
  }
}
