package com.github.theguuholi.rentcars.cars;

import com.github.theguuholi.rentcars.shared.storage.ImageCleanup;
import com.github.theguuholi.rentcars.shared.storage.ImageStorage;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class CarImageService {
  private final CarService cars;
  private final CarImageRepository images;
  private final CarImageValidator validator;
  private final ImageStorage storage;
  private final ImageCleanup cleanup;

  CarImageService(
      CarService cars,
      CarImageRepository images,
      CarImageValidator validator,
      ImageStorage storage,
      ImageCleanup cleanup) {
    this.cars = cars;
    this.images = images;
    this.validator = validator;
    this.storage = storage;
    this.cleanup = cleanup;
  }

  @Transactional
  Car create(CarRequest request, List<MultipartFile> files) {
    if (files.size() > 6)
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, "At most six images are allowed per request");
    var validated = files.stream().map(validator::validate).toList();
    Car car = cars.create(request);
    for (var file : validated) {
      UUID imageId = UUID.randomUUID();
      String key = "cars/" + car.id() + "/" + imageId + "." + file.extension();
      cleanup.prepare(key);
      cleanup.lock(key);
      storage.store(key, file.bytes(), file.contentType());
      images.save(new CarImage(car, imageId, key, file.contentType(), file.bytes().length));
      cleanup.complete(key);
    }
    images.flush();
    return car;
  }

  record Download(byte[] bytes, String contentType) {}

  @Transactional(readOnly = true)
  Download get(UUID carId, UUID imageId) {
    var image = find(carId, imageId);
    return new Download(storage.load(image.key()), image.contentType());
  }

  @Transactional
  void delete(UUID carId, UUID imageId) {
    images.delete(find(carId, imageId));
    images.flush();
  }

  private CarImage find(UUID carId, UUID imageId) {
    return images
        .findByIdAndCarId(imageId, carId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car image not found"));
  }
}
