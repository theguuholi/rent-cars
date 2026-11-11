package com.github.theguuholi.rentcars.cars;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

interface CarRepository extends JpaRepository<Car, UUID> {
  @Query(
      """
      select c from Car c join c.category category
      where (:availableOnly = false or c.available = true)
        and lower(c.name) like :namePattern escape '!'
        and lower(c.brand) like :brandPattern escape '!'
        and lower(category.name) like :categoryPattern escape '!'
      """)
  Page<Car> search(
      boolean availableOnly,
      String namePattern,
      String brandPattern,
      String categoryPattern,
      Pageable pageable);

  @EntityGraph(
      type = EntityGraph.EntityGraphType.LOAD,
      attributePaths = {"category", "specifications", "images"})
  Optional<Car> findDetailedById(UUID id);

  @Query(
      value = "select exists(select 1 from rentals where car_id = :id and end_date is null)",
      nativeQuery = true)
  boolean hasOpenRental(UUID id);

  @Query(value = "select exists(select 1 from rentals where car_id = :id)", nativeQuery = true)
  boolean hasRentalHistory(UUID id);

  // Serialize deletion; PATCH uses the optimistic version from the editor.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Car c where c.id = :id")
  Optional<Car> findForUpdate(UUID id);
}
