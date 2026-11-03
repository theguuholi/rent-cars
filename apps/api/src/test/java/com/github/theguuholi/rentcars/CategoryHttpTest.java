package com.github.theguuholi.rentcars;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  com.github.theguuholi.rentcars.auth.AdminRequests.class
})
@Transactional
class CategoryHttpTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void listsCategoriesAlphabeticallyAsJson() throws Exception {
    jdbc.update("delete from categories");
    jdbc.update(
        "insert into categories (name, description) values (?, ?), (?, ?)",
        "VAN",
        "Passenger van",
        "COUPE",
        "Two-door car");
    var expectedId =
        jdbc.queryForObject("select id::text from categories where name = 'COUPE'", String.class);
    mvc.perform(get("/categories").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains("COUPE", "VAN")))
        .andExpect(jsonPath("$[0].id").value(expectedId))
        .andExpect(jsonPath("$[0].description").value("Two-door car"));
  }

  @Test
  void emptyCatalogReturnsAnEmptyArray() throws Exception {
    jdbc.update("delete from categories");
    mvc.perform(get("/categories"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", org.springframework.test.json.JsonCompareMode.STRICT));
  }

  @Test
  void publishesCategoryOperationAndServesSwaggerUi() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Rent Cars API"))
        .andExpect(
            jsonPath("$.components.schemas.Category.properties.name.example").value("HATCHBACK"))
        .andExpect(
            jsonPath("$.components.schemas.Category.properties.description.maxLength").value(255))
        .andExpect(
            jsonPath(
                    "$.paths['/categories'].get.responses['200'].content['application/json'].schema.items['$ref']")
                .value("#/components/schemas/Category"))
        .andExpect(
            jsonPath("$.paths['/categories'].get.summary").value("List categories alphabetically"));
    mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
