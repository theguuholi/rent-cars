package com.github.theguuholi.rentcars.cars;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class CarService {
  private final CarRepository cars;
  // The validator reads rules such as @Min and @NotBlank and reports which fields failed.
  private final Validator validator;

  CarService(CarRepository cars, Validator validator) {
    this.cars = cars;
    this.validator = validator;
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
    return save(cars.findForUpdate(id).orElseThrow(CarService::notFound), request);
  }

  @Transactional
  void delete(UUID id) {
    cars.delete(cars.findForUpdate(id).orElseThrow(CarService::notFound));
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
