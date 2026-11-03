package com.github.theguuholi.rentcars.specifications;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface SpecificationRepository extends JpaRepository<Specification, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Specification s where s.id = :id")
  Optional<Specification> findForUpdate(@Param("id") UUID id);
}
