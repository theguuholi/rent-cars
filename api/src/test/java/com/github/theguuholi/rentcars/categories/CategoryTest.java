package com.github.theguuholi.rentcars.categories;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Category")
class CategoryTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        factory.close();
    }

    @ParameterizedTest(name = "[{index}] blank name: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Rejects a missing, empty, or whitespace-only name")
    void rejectsBlankName(String name) {
        var violations = validator.validateValue(Category.class, "name", name);

        assertThat(violations).singleElement().extracting(v -> v.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(NotBlank.class);
    }

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = {1, 49, 50})
    @DisplayName("Accepts a nonblank name up to and including 50 characters")
    void acceptsNameWithinLimit(int length) {
        assertThat(validator.validateValue(Category.class, "name", "A".repeat(length))).isEmpty();
    }

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = {51, 100})
    @DisplayName("Rejects a name longer than 50 characters")
    void rejectsNameOverLimit(int length) {
        var violations = validator.validateValue(Category.class, "name", "A".repeat(length));

        assertThat(violations).singleElement().extracting(v -> v.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(Size.class);
    }

    @Test
    @DisplayName("Creating a category stores the uppercase name and the original description")
    void createsCategory() {
        var category = new Category("sUv", "Sport utility vehicle.");

        assertThat(category.getName()).isEqualTo("SUV");
        assertThat(category.getDescription()).isEqualTo("Sport utility vehicle.");
        assertThat(validator.validate(category)).isEmpty();
    }

    @Test
    @DisplayName("Renaming a category normalizes its new name and preserves its description")
    void renamesCategory() {
        var category = new Category("van", "Passenger vehicle.");

        category.rename("miniVan");

        assertThat(category.getName()).isEqualTo("MINIVAN");
        assertThat(category.getDescription()).isEqualTo("Passenger vehicle.");
    }

    @Test
    @DisplayName("Changing the description preserves the category name")
    void changesDescription() {
        var category = new Category("van", "Passenger vehicle.");

        category.changeDescription("Compact passenger van.");

        assertThat(category.getDescription()).isEqualTo("Compact passenger van.");
        assertThat(category.getName()).isEqualTo("VAN");
    }

    @Test
    @DisplayName("A missing name can be reported by Bean Validation during creation and renaming")
    void reportsMissingName() {
        var category = new Category(null, "Passenger vehicle.");
        assertThat(validator.validateProperty(category, "name")).hasSize(1);

        category.rename("van");
        assertThat(validator.validateProperty(category, "name")).isEmpty();

        category.rename(null);
        assertThat(validator.validateProperty(category, "name")).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Rejects a missing, empty, or whitespace-only description")
    void rejectsBlankDescription(String description) {
        var violations = validator.validateValue(Category.class, "description", description);

        assertThat(violations).singleElement()
                .extracting(v -> v.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(NotBlank.class);
    }

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = {1, 254, 255})
    @DisplayName("Accepts a nonblank description up to and including 255 characters")
    void acceptsDescriptionWithinLimit(int length) {
        assertThat(validator.validateValue(Category.class, "description", "a".repeat(length))).isEmpty();
    }

    @Test
    @DisplayName("Rejects a description that exceeds 255 characters")
    void rejectsDescriptionOverLimit() {
        var violations = validator.validateValue(Category.class, "description", "a".repeat(256));

        assertThat(violations).singleElement()
                .extracting(v -> v.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(Size.class);
    }
}
