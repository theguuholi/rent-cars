package com.github.theguuholi.rentcars.categories;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;

@Tag(name = "Categories", description = "Browse the body styles used to classify rental vehicles.")
@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @Operation(summary = "List categories alphabetically",
            description = "Use this list to populate a category picker or display a guide to vehicle body styles. "
                    + "The request takes no parameters and returns the full catalog sorted by name.")
    @ApiResponse(responseCode = "200", description = "The category list, or an empty array if the catalog is empty.",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = Category.class))))
    @GetMapping
    public List<Category> list() {
        return categories.findAll(Sort.by("name").ascending());
    }

}
