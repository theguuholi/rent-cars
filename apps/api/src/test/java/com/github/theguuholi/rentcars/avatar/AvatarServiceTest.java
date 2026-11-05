package com.github.theguuholi.rentcars.avatar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import com.github.theguuholi.rentcars.auth.AccountRepository;
import com.github.theguuholi.rentcars.images.*;
import java.io.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AvatarServiceTest {
  @TempDir static Path root;

  @DynamicPropertySource
  static void storage(DynamicPropertyRegistry r) {
    r.add("rent.storage.directory", () -> root.toString());
  }

  @Autowired AvatarService avatars;
  @Autowired ImageCleanup cleanup;
  @Autowired AccountRepository accounts;
  @Autowired ImageObjectRepository objects;
  @Autowired JdbcClient db;
  @Autowired TransactionTemplate tx;
  @Autowired ImageStorage storage;
  UUID account;

  @BeforeEach
  void account() {
    account = UUID.randomUUID();
    db.sql("insert into accounts(id,email,password_hash) values(?,?,?)")
        .params(account, account + "@example.test", "unused")
        .update();
  }

  @Nested
  class Replace {
    @Test
    @DisplayName(
        "Given a caller transaction, when it rolls back after uploading, then the completed avatar operation remains independently committed")
    void independentOperation() throws Exception {
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      _ -> {
                        avatars.replace(account, new byte[] {7});
                        throw new IllegalStateException("caller rollback");
                      }))
          .isInstanceOf(IllegalStateException.class);
      assertThat(avatars.read(account)).containsExactly(7);
    }

    @Test
    @DisplayName(
        "Given an avatar, when the replacement storage fails, then the old pointer and old bytes remain readable")
    void failure() throws Exception {
      avatars.replace(account, new byte[] {1});
      var broken = mock(ImageStorage.class);
      doThrow(new IOException("offline")).when(broken).write(any(), any());
      var service = new AvatarService(accounts, objects, broken, tx);
      assertThatThrownBy(() -> service.replace(account, new byte[] {2}))
          .isInstanceOf(UncheckedIOException.class);
      assertThat(avatars.read(account)).containsExactly(1);
    }

    @Test
    @DisplayName(
        "Given an existing avatar, when the cloud SDK fails during replacement, then roll back the pointer and retain an orphan allocation for cleanup")
    void cloudFailure() throws Exception {
      avatars.replace(account, new byte[] {4});
      var before = objects.count();
      var broken = mock(ImageStorage.class);
      doThrow(new IllegalStateException("cloud unavailable")).when(broken).write(any(), any());
      var service = new AvatarService(accounts, objects, broken, tx);
      assertThatThrownBy(() -> service.replace(account, new byte[] {5}))
          .isInstanceOf(IllegalStateException.class);
      assertThat(avatars.read(account)).containsExactly(4);
      assertThat(objects.count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName(
        "Given two concurrent replacements, when both finish, then one complete object is referenced and cleanup retains it")
    void concurrent() throws Exception {
      var start = new java.util.concurrent.CyclicBarrier(2);
      try (var threads = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        var a =
            threads.submit(
                () -> {
                  start.await();
                  avatars.replace(account, new byte[] {1});
                  return true;
                });
        var b =
            threads.submit(
                () -> {
                  start.await();
                  avatars.replace(account, new byte[] {2});
                  return true;
                });
        a.get(10, java.util.concurrent.TimeUnit.SECONDS);
        b.get(10, java.util.concurrent.TimeUnit.SECONDS);
      }
      byte[] current = avatars.read(account);
      assertThat(current[0]).isIn((byte) 1, (byte) 2);
      db.sql("update image_objects set created_at=now()-interval '2 hours'").update();
      cleanup.clean();
      assertThat(avatars.read(account)).isEqualTo(current);
    }

    @Test
    @DisplayName("Given a deleted account, when uploading, then reject the stale identity")
    void missing() {
      var id = UUID.randomUUID();
      assertThatThrownBy(() -> avatars.replace(id, new byte[] {1})).hasMessageContaining("401");
    }
  }

  @Nested
  class Remove {
    @Test
    @DisplayName(
        "Given an avatar, when removing twice, then reads return 404 and cleanup can remove the object")
    void delete() {
      avatars.replace(account, new byte[] {1});
      avatars.remove(account);
      avatars.remove(account);
      assertThatThrownBy(() -> avatars.read(account)).hasMessageContaining("404");
    }
  }

  @Nested
  class Clean {
    @Test
    @DisplayName(
        "Given a candidate became referenced after selection, when cleanup rechecks it, then leave its object intact")
    void newlyReferenced() throws Exception {
      avatars.replace(account, new byte[] {3});
      var key =
          db.sql("select avatar_key from accounts where id=?")
              .param(account)
              .query(UUID.class)
              .single();
      var racing = mock(ImageObjectRepository.class);
      when(racing.deleteUnreferenced(key)).thenAnswer(_ -> objects.deleteUnreferenced(key));
      doReturn(java.util.List.of(new ImageObject(key))).when(racing).lockOrphans();
      var service = new ImageCleanup(racing, storage);
      tx.executeWithoutResult(_ -> service.clean());
      assertThat(avatars.read(account)).containsExactly(3);
    }

    @Test
    @DisplayName(
        "Given an old orphan with a failed deletion, when retrying cleanup, then keep the allocation until deletion succeeds")
    void retry() throws Exception {
      var key = UUID.randomUUID();
      db.sql("insert into image_objects(id,created_at) values(?,now()-interval '2 hours')")
          .param(key)
          .update();
      storage.write(key, new byte[] {1});
      var broken = mock(ImageStorage.class);
      doThrow(new IOException("offline")).when(broken).delete(key);
      var service = new ImageCleanup(objects, broken);
      assertThatThrownBy(
              () ->
                  tx.executeWithoutResult(
                      _ -> {
                        service.clean();
                      }))
          .isInstanceOf(UncheckedIOException.class);
      assertThat(
              db.sql("select count(*) from image_objects where id=?")
                  .param(key)
                  .query(Long.class)
                  .single())
          .isEqualTo(1L);
      cleanup.clean();
      assertThat(
              db.sql("select count(*) from image_objects where id=?")
                  .param(key)
                  .query(Long.class)
                  .single())
          .isZero();
      assertThatThrownBy(() -> storage.read(key)).isInstanceOf(IOException.class);
      cleanup.clean();
    }
  }
}
