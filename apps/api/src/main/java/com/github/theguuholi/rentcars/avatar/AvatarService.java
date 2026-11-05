package com.github.theguuholi.rentcars.avatar;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class AvatarService {
  private final JdbcClient db;
  private final ImageStorage storage;
  private final TransactionTemplate tx;

  AvatarService(JdbcClient db, ImageStorage storage, TransactionTemplate tx) {
    this.db = db;
    this.storage = storage;
    this.tx = tx;
  }

  void replace(UUID account, byte[] image) {
    var key = UUID.randomUUID();
    // Reserve independently so a crash or rolled-back write leaves a discoverable orphan.
    db.sql("insert into avatar_objects(id) values (?)").param(key).update();
    tx.executeWithoutResult(
        status -> {
          db.sql("select id from avatar_objects where id=? for update")
              .param(key)
              .query(UUID.class)
              .single();
          change(account, key);
          try {
            storage.write(key, image);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
  }

  private void change(UUID account, UUID key) {
    // PostgreSQL serializes concurrent updates to this row. The pointer is visible only after
    // commit.
    if (db.sql("update accounts set avatar_key=:key where id=:account")
            .param("key", key, java.sql.Types.OTHER)
            .param("account", account)
            .update()
        == 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  @Transactional
  void remove(UUID account) {
    change(account, null);
  }

  @Transactional
  byte[] read(UUID account) throws IOException {
    var key =
        db.sql("select avatar_key from accounts where id=? and avatar_key is not null for share")
            .param(account)
            .query(UUID.class)
            .optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return storage.read(key);
  }

  @Scheduled(fixedDelayString = "${rent.storage.cleanup-delay:PT10M}")
  @Transactional
  void clean() {
    var keys =
        db.sql(
                """
        select o.id from avatar_objects o where o.created_at < now()-interval '1 hour'
        and not exists(select 1 from accounts a where a.avatar_key=o.id)
        order by o.created_at limit 100 for update skip locked
        """)
            .query(UUID.class)
            .list();
    for (var key : keys) {
      // Recheck after acquiring the lock in case an in-flight writer committed while selecting.
      if (db.sql(
                  "delete from avatar_objects o where id=? and not exists(select 1 from accounts a where a.avatar_key=o.id)")
              .param(key)
              .update()
          == 1) {
        try {
          storage.delete(key);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
