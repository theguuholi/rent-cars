package com.github.theguuholi.rentcars.avatar;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AvatarObjectRepository extends JpaRepository<AvatarObject, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from AvatarObject o where o.id = :id")
  AvatarObject lockAllocation(@Param("id") UUID id);

  /** PostgreSQL SKIP LOCKED lets replicas clean different batches without blocking uploads. */
  @Query(
      value =
          """
      select o.* from avatar_objects o where o.created_at < now()-interval '1 hour'
      and not exists(select 1 from accounts a where a.avatar_key=o.id)
      order by o.created_at limit 100 for update skip locked
      """,
      nativeQuery = true)
  List<AvatarObject> lockOrphans();

  @Modifying
  @Query(
      value =
          "delete from avatar_objects o where id=:id and not exists(select 1 from accounts a where a.avatar_key=o.id)",
      nativeQuery = true)
  int deleteUnreferenced(@Param("id") UUID id);
}
