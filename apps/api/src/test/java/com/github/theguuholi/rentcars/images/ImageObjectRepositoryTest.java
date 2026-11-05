package com.github.theguuholi.rentcars.images;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ImageObjectRepositoryTest {
  @Autowired ImageObjectRepository objects;
  @Autowired JdbcClient db;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;

  UUID allocate(boolean old) {
    var id = UUID.randomUUID();
    objects.saveAndFlush(new ImageObject(id));
    if (old)
      db.sql("update image_objects set created_at=now()-interval '2 hours' where id=?")
          .param(id)
          .update();
    return id;
  }

  void reference(UUID key) {
    var account = UUID.randomUUID();
    db.sql("insert into accounts(id,email,password_hash,avatar_key) values(?,?,?,?)")
        .params(account, account + "@example.test", "unused", key)
        .update();
  }

  @Nested
  class LockAllocation {
    @Test
    @DisplayName(
        "Given a committed allocation, when locking for upload, then load the original storage ID")
    void lock() {
      var key = allocate(false);
      assertThat(objects.lockAllocation(key).id()).isEqualTo(key);
    }
  }

  @Nested
  class LockOrphans {
    @Test
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DisplayName(
        "Given an allocation locked by an upload, when another transaction cleans, then skip that object until the upload releases its lock")
    void skipLockedUpload() throws Exception {
      var key = allocate(true);
      var locked = new java.util.concurrent.CountDownLatch(1);
      var release = new java.util.concurrent.CountDownLatch(1);
      try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        var upload =
            pool.submit(
                () ->
                    tx.executeWithoutResult(
                        _ -> {
                          objects.lockAllocation(key);
                          locked.countDown();
                          try {
                            assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                .isTrue();
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                          }
                        }));
        try {
          assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
          tx.executeWithoutResult(
              _ ->
                  assertThat(objects.lockOrphans())
                      .extracting(ImageObject::id)
                      .doesNotContain(key));
        } finally {
          release.countDown();
        }
        upload.get(10, java.util.concurrent.TimeUnit.SECONDS);
        tx.executeWithoutResult(
            _ -> {
              assertThat(objects.lockOrphans()).extracting(ImageObject::id).contains(key);
              assertThat(objects.deleteUnreferenced(key)).isOne();
            });
      }
    }

    @Test
    @DisplayName(
        "Given more than one batch of old orphans and an object exactly at the grace boundary, when cleaning, then limit the batch and exclude the boundary")
    void boundedBatch() {
      db.sql(
              "insert into image_objects(id,created_at) select gen_random_uuid(),now()-interval '2 hours' from generate_series(1,101)")
          .update();
      var boundary = allocate(false);
      db.sql("update image_objects set created_at=now()-interval '1 hour' where id=?")
          .param(boundary)
          .update();
      assertThat(objects.lockOrphans())
          .hasSize(100)
          .extracting(ImageObject::id)
          .doesNotContain(boundary);
    }

    @Test
    @DisplayName(
        "Given old, recent and referenced objects, when selecting cleanup candidates, then only old orphans are eligible")
    void eligible() {
      var old = allocate(true);
      var recent = allocate(false);
      var referenced = allocate(true);
      reference(referenced);
      assertThat(objects.lockOrphans())
          .extracting(ImageObject::id)
          .contains(old)
          .doesNotContain(recent, referenced);
    }
  }

  @Nested
  class DeleteUnreferenced {
    @Test
    @DisplayName(
        "Given referenced and orphan objects, when deleting, then retain live references and delete an orphan once")
    void recheck() {
      var referenced = allocate(true);
      reference(referenced);
      var orphan = allocate(true);
      assertThat(objects.deleteUnreferenced(referenced)).isZero();
      assertThat(objects.deleteUnreferenced(orphan)).isOne();
      assertThat(objects.deleteUnreferenced(orphan)).isZero();
      assertThat(objects.existsById(referenced)).isTrue();
    }
  }
}
