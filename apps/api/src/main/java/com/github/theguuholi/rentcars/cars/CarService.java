package com.github.theguuholi.rentcars.cars;

import com.github.theguuholi.rentcars.images.ImageNormalizer;
import com.github.theguuholi.rentcars.images.ImageObject;
import com.github.theguuholi.rentcars.images.ImageObjectRepository;
import com.github.theguuholi.rentcars.images.ImageStorage;
import com.github.theguuholi.rentcars.shared.http.EditVersion;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class CarService {
  private final CarRepository cars;
  // The validator reads rules such as @Min and @NotBlank and reports which fields failed.
  private final Validator validator;

  private final CarImageRepository images;
  private final ImageObjectRepository objects;
  private final ImageStorage storage;
  private final TransactionTemplate tx;

  CarService(
      CarRepository cars,
      Validator validator,
      CarImageRepository images,
      ImageObjectRepository objects,
      ImageStorage storage,
      TransactionTemplate tx) {
    this.cars = cars;
    this.validator = validator;
    this.images = images;
    this.objects = objects;
    this.storage = storage;
    this.tx = tx;
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
    return save(new Car(), request);
  }

  @Transactional
  Car update(UUID id, CarRequest request) {
    var car = cars.findById(id).orElseThrow(CarService::notFound);
    EditVersion.requireCurrent(request.expectedVersion, car.getVersion());
    return save(car, request);
  }

  @Transactional
  void delete(UUID id) {
    cars.delete(cars.findForUpdate(id).orElseThrow(CarService::notFound));
  }

  @Transactional(readOnly = true)
  List<CarImage> listImages(UUID car) {
    if (!cars.existsById(car)) throw imageNotFound();
    return images.findByCarIdOrderByCreatedAtAscIdAsc(car);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  CarImage addImage(UUID car, MultipartFile image) throws IOException {
    var bytes = ImageNormalizer.normalize(image, 1600);
    var key = UUID.randomUUID();
    objects.saveAndFlush(new ImageObject(key));
    return tx.execute(
        _ -> {
          objects.lockAllocation(key);
          cars.findForUpdate(car).orElseThrow(CarService::imageNotFound);
          try {
            storage.write(key, bytes);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
          return images.saveAndFlush(new CarImage(key, car));
        });
  }

  @Transactional
  Resource readImage(UUID car, UUID image) throws IOException {
    // Coordinate reads with deletion of the gallery entry or its parent car.
    cars.findForRead(car).orElseThrow(CarService::imageNotFound);
    var entry = images.findByIdAndCarId(image, car).orElseThrow(CarService::imageNotFound);
    return new ByteArrayResource(storage.read(entry.getId()));
  }

  @Transactional
  void removeImage(UUID car, UUID image) {
    cars.findForUpdate(car).orElseThrow(CarService::imageNotFound);
    images.delete(images.findByIdAndCarId(image, car).orElseThrow(CarService::imageNotFound));
  }

  private static ResponseStatusException imageNotFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Car or image not found");
  }

  private Car save(Car car, CarRequest request) {
    // Check supplied charges before constructing BrlAmount, then check required fields on
    // the merged car. A PATCH can omit a required field that the stored car already has.
    validate(request);
    car.apply(request);
    validate(car);
    return cars.saveAndFlush(car);
  }

  private <T> void validate(T value) {
    var violations = validator.validate(value);
    if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found");
  }
}
