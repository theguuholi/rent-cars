package com.github.theguuholi.rentcars.cars;

import com.github.theguuholi.rentcars.shared.http.EditVersion;
import com.github.theguuholi.rentcars.shared.storage.ImageStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class CarService {
  private static final Logger log = LoggerFactory.getLogger(CarService.class);
  private final CarRepository cars;
  private final Validator validator;
  private final ImageStorage storage;
  private final EntityManager entities;

  CarService(
      CarRepository cars, Validator validator, ImageStorage storage, EntityManager entities) {
    this.cars = cars;
    this.validator = validator;
    this.storage = storage;
    this.entities = entities;
  }

  List<Car> list() {
    return cars.findAll(Sort.by("name", "id"));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  Car get(UUID id) {
    return cars.findDetailedById(id).orElseThrow(CarService::notFound);
  }

  @Transactional
  Car create(CarRequest request) {
    return create(request, List.of());
  }

  @Transactional
  Car create(CarRequest request, List<MultipartFile> files) {
    Car car = new Car();
    apply(car, request);
    changeImages(car, files, request.removeImageIds);
    return cars.saveAndFlush(car);
  }

  @Transactional
  Car update(UUID id, CarRequest request) {
    return update(id, request, List.of());
  }

  @Transactional
  Car update(UUID id, CarRequest request, List<MultipartFile> files) {
    var car = cars.findById(id).orElseThrow(CarService::notFound);
    EditVersion.requireCurrent(request.expectedVersion, car.getVersion());
    apply(car, request);
    if (changeImages(car, files, request.removeImageIds)) {
      entities.lock(car, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
    return cars.saveAndFlush(car);
  }

  @Transactional
  void delete(UUID id) {
    Car car = cars.findForUpdate(id).orElseThrow(CarService::notFound);
    var keys = car.images().stream().map(CarImage::key).toList();
    if (!keys.isEmpty()) cleanupAfterTransaction(List.of(), keys);
    cars.delete(car);
  }

  private void apply(Car car, CarRequest request) {
    validate(request);
    car.apply(request);
    validate(car);
  }

  private boolean changeImages(Car car, List<MultipartFile> files, Set<UUID> removeIds) {
    Set<UUID> removed = removeIds == null ? Set.of() : removeIds;
    var oldImages = car.images().stream().filter(image -> removed.contains(image.id())).toList();
    if (oldImages.size() != removed.size()) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, "Every removed image must belong to this car");
    }
    if (files.isEmpty() && oldImages.isEmpty()) return false;
    // Obtain the generated car ID before composing file keys; this does not commit the transaction.
    cars.saveAndFlush(car);
    var uploadedKeys = new ArrayList<String>();
    cleanupAfterTransaction(uploadedKeys, oldImages.stream().map(CarImage::key).toList());
    car.images().removeAll(oldImages);
    for (MultipartFile file : files) car.images().add(storeImage(car, file, uploadedKeys));
    return true;
  }

  private CarImage storeImage(Car car, MultipartFile file, List<String> uploadedKeys) {
    try {
      byte[] bytes = file.getBytes();
      try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) throw invalidImage();
        var reader = readers.next();
        try {
          String format = reader.getFormatName();
          if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG"))
            throw invalidImage();
          reader.setInput(stream);
          if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw invalidImage();
          reader.read(0);
          boolean jpeg = format.equalsIgnoreCase("JPEG");
          String contentType = jpeg ? "image/jpeg" : "image/png";
          UUID imageId = UUID.randomUUID();
          String key = "cars/" + car.id() + "/" + imageId + (jpeg ? ".jpg" : ".png");
          uploadedKeys.add(key);
          storage.store(key, bytes, contentType);
          return new CarImage(car, imageId, key, contentType, bytes.length);
        } finally {
          reader.dispose();
        }
      }
    } catch (IOException _) {
      throw invalidImage();
    }
  }

  private void cleanupAfterTransaction(List<String> uploadedKeys, List<String> removedKeys) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            List<String> keys = status == STATUS_COMMITTED ? removedKeys : uploadedKeys;
            for (String key : keys) {
              try {
                storage.delete(key);
              } catch (RuntimeException exception) {
                log.warn("Could not delete image {}; manual cleanup is required", key, exception);
              }
            }
          }
        });
  }

  private static ResponseStatusException invalidImage() {
    return new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Provide a valid JPEG or PNG image with at most 20 million pixels");
  }

  private <T> void validate(T value) {
    var violations = validator.validate(value);
    if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found");
  }
}
