package com.github.theguuholi.rentcars.cars;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CarImageRepository extends JpaRepository<CarImage, UUID> {
  Optional<CarImage> findByIdAndCarId(UUID id, UUID carId);
}
