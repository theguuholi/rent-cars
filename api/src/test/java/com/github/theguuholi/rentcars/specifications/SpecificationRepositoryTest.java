package com.github.theguuholi.rentcars.specifications;

import static org.assertj.core.api.Assertions.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

class SpecificationRepositoryTest {
  @Test
  void modificationLookupDeclaresAWriteLock() throws NoSuchMethodException {
    var method = SpecificationRepository.class.getMethod("findForUpdate", UUID.class);
    assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
  }

  @org.junit.jupiter.api.Nested
  @SpringBootTest
  @Import(TestcontainersConfiguration.class)
  @Transactional
  class OnPostgres {
    @Autowired SpecificationRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void lookupLocksPersistedRecordAndReturnsEmptyForUnknownId() {
      var attrs = new SpecificationRequest.Attributes();
      attrs.name = "gps";
      attrs.description = "Navigation";
      var item = new Specification();
      item.apply(attrs);
      repository.saveAndFlush(item);
      var id = item.getId();
      entityManager.clear();
      var locked = repository.findForUpdate(id).orElseThrow();
      assertThat(locked.getName()).isEqualTo("GPS");
      assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
      assertThat(repository.findForUpdate(UUID.randomUUID())).isEmpty();
    }
  }
}
