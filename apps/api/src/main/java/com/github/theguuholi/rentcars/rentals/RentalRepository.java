package com.github.theguuholi.rentcars.rentals;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RentalRepository extends JpaRepository<Rental, UUID> {
  boolean existsByAccountIdAndEndDateIsNull(UUID accountId);

  @EntityGraph(attributePaths = "car")
  List<Rental> findByAccountIdOrderByStartDateDescIdDesc(UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Rental r where r.id = :id and r.accountId = :accountId")
  Optional<Rental> findForReturn(UUID id, UUID accountId);
}
