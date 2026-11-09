package com.github.theguuholi.rentcars.cars;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Joins the rental transaction; catalog repositories remain private to the cars package. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CarRentalAccess {
  private final CarRepository cars;

  CarRentalAccess(CarRepository cars) {
    this.cars = cars;
  }

  public Car getForRental(UUID id) {
    return cars.findForUpdate(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Car not found"));
  }
}
