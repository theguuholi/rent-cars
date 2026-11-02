package com.github.theguuholi.rentcars.specifications;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpecificationServiceTest {
  private static ValidatorFactory factory;
  private final SpecificationRepository repository = mock();
  private SpecificationService service;

  @BeforeAll
  static void open() {
    factory = Validation.buildDefaultValidatorFactory();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @BeforeEach
  void setUp() {
    service = new SpecificationService(repository, factory.getValidator());
  }

  @Test
  void listsByNameAndReadsById() {
    var item = new Specification();
    var id = UUID.randomUUID();
    when(repository.findAll(Sort.by("name"))).thenReturn(List.of(item));
    when(repository.findById(id)).thenReturn(Optional.of(item));
    assertThat(service.list()).containsExactly(item);
    assertThat(service.get(id)).isSameAs(item);
  }

  @Test
  void createsValidatedNormalizedSpecification() {
    var attrs = new SpecificationRequest();
    attrs.name = "gps";
    attrs.description = "Navigation";
    when(repository.saveAndFlush(any(Specification.class))).thenAnswer(call -> call.getArgument(0));
    var item = service.create(attrs);
    assertThat(item.getName()).isEqualTo("GPS");
    assertThat(item.getDescription()).isEqualTo("Navigation");
    verify(repository).saveAndFlush(item);
  }

  @Test
  void rejectsInvalidCreationBeforePersistence() {
    var invalidRequest = new SpecificationRequest();
    assertThatThrownBy(() -> service.create(invalidRequest))
        .isInstanceOf(ConstraintViolationException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void locksExistingRecordForUpdateAndDeletion() {
    var id = UUID.randomUUID();
    var item = new Specification();
    var attrs = new SpecificationRequest();
    attrs.name = "gps";
    attrs.description = "Navigation";
    item.apply(attrs);
    when(repository.findForUpdate(id)).thenReturn(Optional.of(item));
    when(repository.saveAndFlush(item)).thenReturn(item);
    var patch = new SpecificationRequest();
    patch.description = "Updated";
    assertThat(service.update(id, patch)).isSameAs(item);
    assertThat(item.getName()).isEqualTo("GPS");
    assertThat(item.getDescription()).isEqualTo("Updated");
    service.delete(id);
    verify(repository, times(2)).findForUpdate(id);
    verify(repository).delete(item);
  }

  @Test
  void missingRecordsProduceNotFoundWithoutWrites() {
    var id = UUID.randomUUID();
    for (Runnable operation :
        List.<Runnable>of(
            () -> service.get(id),
            () -> service.update(id, new SpecificationRequest()),
            () -> service.delete(id))) {
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(operation::run)
          .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
    verify(repository, never()).saveAndFlush(any());
    verify(repository, never()).delete(any());
  }
}
