package com.github.theguuholi.rentcars.cars;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface CarRepository extends JpaRepository<Car, UUID> {
  // PostgreSQL locks this car until commit. A competing PATCH or DELETE waits so it
  // sees the first write before changing the car or replacing its specifications.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Car c where c.id = :id")
  Optional<Car> findForUpdate(UUID id);
}
