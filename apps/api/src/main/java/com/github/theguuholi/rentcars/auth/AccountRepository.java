package com.github.theguuholi.rentcars.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByEmail(String email);

  @Query(value = "select exists(select 1 from rentals where account_id = :id)", nativeQuery = true)
  boolean hasRentalHistory(UUID id);
}
