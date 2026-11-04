package com.github.theguuholi.rentcars.shared.http;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EditVersionTest {
  @Nested
  class RequireCurrent {
    @Test
    @DisplayName(
        "Given an unchanged version above the boxed integer cache, when editing, then compare its numeric value")
    void acceptsCurrent() {
      assertThatCode(() -> EditVersion.requireCurrent(300L, 300L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "Given a stale version, when checking the open form, then reject the edit before applying any fields")
    void rejectsStale() {
      assertThatThrownBy(() -> EditVersion.requireCurrent(0L, 1L))
          .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName(
        "Given an omitted version, when editing an existing record, then require a version")
    void requiresVersion() {
      assertThatExceptionOfType(ResponseStatusException.class)
          .isThrownBy(() -> EditVersion.requireCurrent(null, 0))
          .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
  }
}
