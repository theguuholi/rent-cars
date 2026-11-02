package com.github.theguuholi.rentcars.categories;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Category controller")
class CategoryControllerTest {

    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final CategoryController controller = new CategoryController(categories);
    private final Sort byName = Sort.by(Sort.Direction.ASC, "name");

    @Test
    @DisplayName("Requests categories in ascending name order and returns them in that order")
    void listsCategoriesByName() {
        var sedan = new Category("SEDAN", "Passenger car");
        var van = new Category("VAN", "Passenger van");
        when(categories.findAll(byName)).thenReturn(List.of(sedan, van));

        assertThat(controller.list()).containsExactly(sedan, van);
        verify(categories).findAll(byName);
    }

    @Test
    @DisplayName("Returns an empty list when the catalog has no categories")
    void listsEmptyCatalog() {
        when(categories.findAll(byName)).thenReturn(List.of());

        assertThat(controller.list()).isEmpty();
        verify(categories).findAll(byName);
    }
}
