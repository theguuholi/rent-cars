package com.github.theguuholi.rentcars.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DisplayName("Category repository on PostgreSQL")
class CategoryRepositoryTest {

  @Autowired CategoryRepository categories;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbc;

  private UUID idOf(Category category) {
    return (UUID)
        entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(category);
  }

  @Test
  @DisplayName("Lists the four catalog categories installed by Flyway")
  void listsSeedCategories() {
    assertThat(categories.findAll())
        .extracting(Category::getName)
        .contains("HATCHBACK", "SEDAN", "SUV", "PICKUP");
  }

  @Test
  @DisplayName("Saves a normalized name and reloads the description by generated UUID")
  void createsAndReloadsCategory() {
    var saved = categories.saveAndFlush(new Category("vAn", "Passenger van"));
    var id = idOf(saved);
    entityManager.clear();

    assertThat(id).isNotNull();
    var loaded = categories.findById(id).orElseThrow();
    assertThat(loaded.getName()).isEqualTo("VAN");
    assertThat(loaded.getDescription()).isEqualTo("Passenger van");
    assertThat(timestamp("inserted_at", id)).isNotNull();
    assertThat(timestamp("updated_at", id)).isNotNull();
  }

  @Test
  @DisplayName(
      "Changes the name and description, retaining creation time and refreshing update time")
  void updatesExistingCategory() {
    var saved = categories.saveAndFlush(new Category("van", "Passenger van"));
    var id = idOf(saved);
    var created = timestamp("inserted_at", id);
    var oldUpdate = Instant.parse("2000-01-01T00:00:00Z");
    jdbc.update("update categories set updated_at = ? where id = ?", Timestamp.from(oldUpdate), id);
    entityManager.clear();

    var category = categories.findById(id).orElseThrow();
    category.rename("minivan");
    category.changeDescription("Compact passenger van");
    categories.flush();
    entityManager.clear();

    var updated = categories.findById(id).orElseThrow();
    assertThat(updated.getName()).isEqualTo("MINIVAN");
    assertThat(updated.getDescription()).isEqualTo("Compact passenger van");
    assertThat(timestamp("inserted_at", id)).isEqualTo(created);
    assertThat(timestamp("updated_at", id)).isAfter(oldUpdate);
  }

  private Instant timestamp(String column, UUID id) {
    return jdbc.queryForObject(
            "select " + column + " from categories where id = ?", Timestamp.class, id)
        .toInstant();
  }

  @Test
  @DisplayName("Rejects inserting a name already present with different letter case")
  void rejectsDuplicateInsert() {
    assertThatThrownBy(() -> categories.saveAndFlush(new Category("sUv", "Another SUV")))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("categories_name_unique");
  }

  @Test
  @DisplayName("Rejects renaming a category to another category's name")
  void rejectsDuplicateRename() {
    var van = categories.saveAndFlush(new Category("van", "Passenger van"));
    van.rename("sedan");

    assertThatThrownBy(categories::flush)
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasStackTraceContaining("categories_name_unique");
  }

  @Test
  @DisplayName("Deletes a category without removing other catalog entries")
  void deletesCategory() {
    var id = idOf(categories.saveAndFlush(new Category("van", "Passenger van")));
    categories.deleteById(id);
    categories.flush();
    entityManager.clear();

    assertThat(categories.findById(id)).isEmpty();
    assertThat(categories.findAll()).extracting(Category::getName).contains("SUV");
  }

  @Test
  @DisplayName("Returns an empty result for an unknown UUID")
  void handlesMissingCategory() {
    assertThat(categories.findById(UUID.randomUUID())).isEmpty();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  @DisplayName("Rejects a missing or blank name when saving")
  void rejectsInvalidName(String name) {
    assertThatThrownBy(() -> categories.saveAndFlush(new Category(name, "Passenger van")))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void storesCategoryWithoutDescription() {
    var id = idOf(categories.saveAndFlush(new Category("van", null)));
    entityManager.clear();
    assertThat(categories.findById(id).orElseThrow().getDescription()).isNull();
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {"  "})
  @DisplayName("Rejects a blank description when saving")
  void rejectsInvalidDescription(String description) {
    assertThatThrownBy(() -> categories.saveAndFlush(new Category("van", description)))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("Persists a 50-character name and a 255-character description without truncation")
  void acceptsMaximumLengths() {
    var name = "A".repeat(50);
    var description = "x".repeat(255);
    var id = idOf(categories.saveAndFlush(new Category(name, description)));
    entityManager.clear();

    var loaded = categories.findById(id).orElseThrow();
    assertThat(loaded.getName()).isEqualTo(name);
    assertThat(loaded.getDescription()).isEqualTo(description);
  }
}
