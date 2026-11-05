package com.github.theguuholi.rentcars.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  Optional<Account> findByEmail(String email);

  /** The database serializes updates to an account; the caller owns the transaction. */
  @Modifying
  @Query("update Account a set a.avatarKey = :key where a.id = :account")
  int changeAvatar(@Param("account") UUID account, @Param("key") UUID key);

  /** Keep replacement and cleanup from invalidating an object while it is being read. */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select a from Account a where a.id = :account and a.avatarKey is not null")
  Optional<Account> findWithAvatar(@Param("account") UUID account);
}
