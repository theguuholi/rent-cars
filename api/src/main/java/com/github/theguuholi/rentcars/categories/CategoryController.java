package com.github.theguuholi.rentcars.categories;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/categories")
public class CategoryController {
    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<Category> list() {
        return categories.findAll(Sort.by("name").ascending());
    }

}
