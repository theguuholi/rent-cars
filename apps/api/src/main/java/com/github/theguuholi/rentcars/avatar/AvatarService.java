package com.github.theguuholi.rentcars.avatar;

import com.github.theguuholi.rentcars.auth.AccountRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
class AvatarService {
  private final AccountRepository accounts;
  private final AvatarObjectRepository objects;
  private final ImageStorage storage;
  private final TransactionTemplate tx;

  AvatarService(
      AccountRepository accounts,
      AvatarObjectRepository objects,
      ImageStorage storage,
      TransactionTemplate tx) {
    this.accounts = accounts;
    this.objects = objects;
    this.storage = storage;
    this.tx = tx;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replace(UUID account, byte[] image) {
    var key = UUID.randomUUID();
    // Reserve independently so a crash or rolled-back write leaves a discoverable orphan.
    objects.saveAndFlush(new AvatarObject(key));
    tx.executeWithoutResult(
        status -> {
          objects.lockAllocation(key);
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
    if (accounts.changeAvatar(account, key) == 0)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  @Transactional
  void remove(UUID account) {
    change(account, null);
  }

  @Transactional
  byte[] read(UUID account) throws IOException {
    var owner =
        accounts
            .findWithAvatar(account)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return storage.read(owner.avatarKey());
  }

  @Scheduled(fixedDelayString = "${rent.storage.cleanup-delay:PT10M}")
  @Transactional
  void clean() {
    for (var object : objects.lockOrphans()) {
      var key = object.id();
      // Recheck after acquiring the lock in case an in-flight writer committed while selecting.
      if (objects.deleteUnreferenced(key) == 1) {
        try {
          storage.delete(key);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
