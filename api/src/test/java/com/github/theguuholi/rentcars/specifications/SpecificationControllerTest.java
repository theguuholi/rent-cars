package com.github.theguuholi.rentcars.specifications;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class SpecificationControllerTest {
  private final SpecificationService service = mock();
  private final SpecificationController controller = new SpecificationController(service);

  @Test
  void wrapsListAndSingleResult() {
    var item = new Specification();
    var id = UUID.randomUUID();
    when(service.list()).thenReturn(List.of(item));
    when(service.get(id)).thenReturn(item);
    assertThat(controller.list().data()).containsExactly(item);
    assertThat(controller.get(id).data()).isSameAs(item);
  }

  @Test
  void creationIdentifiesNewResourceAndReturnsIt() {
    var attrs = new SpecificationRequest.Attributes();
    var item = mock(Specification.class);
    var id = UUID.randomUUID();
    when(item.getId()).thenReturn(id);
    when(service.create(attrs)).thenReturn(item);
    var result = controller.create(new SpecificationRequest(attrs));
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(result.getHeaders().getLocation()).isEqualTo(URI.create("/specifications/" + id));
    assertThat(result.getBody().data()).isSameAs(item);
  }

  @Test
  void delegatesUpdateAndDeletionUsingRequestedId() {
    var attrs = new SpecificationRequest.Attributes();
    var id = UUID.randomUUID();
    var item = new Specification();
    when(service.update(id, attrs)).thenReturn(item);
    assertThat(controller.update(id, new SpecificationRequest(attrs)).data()).isSameAs(item);
    controller.delete(id);
    verify(service).delete(id);
  }

  @Test
  void handlesOnlyTheNamedUniqueConstraint() {
    var violation = mock(org.hibernate.exception.ConstraintViolationException.class);
    when(violation.getConstraintName()).thenReturn("specifications_name_index");
    var failure = new DataIntegrityViolationException("save failed", violation);
    assertThat(controller.duplicate(failure).errors()).containsOnlyKeys("name");
    when(violation.getConstraintName()).thenReturn("other_constraint");
    assertThatThrownBy(() -> controller.duplicate(failure)).isSameAs(failure);
  }
}
