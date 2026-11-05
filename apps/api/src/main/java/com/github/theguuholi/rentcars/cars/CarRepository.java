package com.github.theguuholi.rentcars.cars;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface CarRepository extends JpaRepository<Car, UUID> {
  @EntityGraph(
      type = EntityGraph.EntityGraphType.LOAD,
      attributePaths = {"category", "specifications", "images"})
  Optional<Car> findDetailedById(UUID id);

  // Serialize deletion; PATCH uses the optimistic version from the editor.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Car c where c.id = :id")
  Optional<Car> findForUpdate(UUID id);
}
