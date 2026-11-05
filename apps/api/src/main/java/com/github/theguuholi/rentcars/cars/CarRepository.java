package com.github.theguuholi.rentcars.cars;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface CarRepository extends JpaRepository<Car, UUID> {
  @EntityGraph(
      type = EntityGraph.EntityGraphType.LOAD,
      attributePaths = {"category", "specifications"})
  Optional<Car> findDetailedById(UUID id);

  // Serialize deletion; PATCH uses the optimistic version from the editor.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Car c where c.id = :id")
  Optional<Car> findForUpdate(UUID id);

  // Readers may share the lock, while gallery/car deletion waits for active reads.
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select c from Car c where c.id = :id")
  Optional<Car> findForRead(UUID id);
}
