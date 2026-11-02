package com.github.theguuholi.categories;

import com.github.theguuholi.rentcars.categories.Category;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Category name validation")
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
}
