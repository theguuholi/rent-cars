package com.github.theguuholi.rentcars.cars;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

interface CarRepository extends JpaRepository<Car, UUID> {
  record CategorizedCar(Car car, String categoryName) {}

  @Query(
      "select c, category.name from Car c join Category category on category.id = c.categoryId where c.id = :id")
  Optional<CategorizedCar> findWithCategory(UUID id);

  @Query(
      """
      select s.id, s.name from Car c join c.specificationIds assigned
      join Specification s on s.id = assigned where c.id = :id order by s.name, s.id
      """)
  List<CarDetail.AssignedSpecification> findAssignedSpecifications(UUID id);

  // PostgreSQL locks this car until commit. A competing PATCH or DELETE waits so it
  // sees the first write before changing the car or replacing its specifications.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Car c where c.id = :id")
  Optional<Car> findForUpdate(UUID id);
}
