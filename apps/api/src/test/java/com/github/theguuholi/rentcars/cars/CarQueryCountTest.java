package com.github.theguuholi.rentcars.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.github.theguuholi.rentcars.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CarQueryCountTest {
  @Autowired MockMvc mvc;
  @Autowired CarRepository cars;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManagerFactory factory;
  @Autowired EntityManager entityManager;
  @Autowired TransactionTemplate tx;
  final List<UUID> ids = new ArrayList<>();
  Statistics statistics;

  @BeforeEach
  void fixtures() {
    statistics = factory.unwrap(SessionFactory.class).getStatistics();
    for (int n = 0; n < 2; n++) {
      jdbc.update(
          "insert into specifications(name,description) values (?, 'Query lab')", "QUERYLAB" + n);
    }
    for (int n = 0; n < 8; n++) {
      var id =
          jdbc.queryForObject(
              """
              insert into cars(name,description,brand,license_plate,category_id,daily_rate_cents,fine_amount_cents)
              select ?, 'Query lab', 'QueryLab', ?, id, 10000, 0 from categories where name='SEDAN' returning id
              """,
              UUID.class,
              "Query car " + n,
              "QUERYLAB" + n);
      ids.add(id);
      jdbc.update(
          "insert into cars_specifications(car_id,specification_id) select ?,id from specifications where name like 'QUERYLAB%'",
          id);
      for (int image = 0; image < 3; image++) {
        jdbc.update(
            "insert into cars_images(id,car_id,storage_key,content_type,size_bytes) values(?,?,?,'image/png',10)",
            UUID.randomUUID(),
            id,
            UUID.randomUUID().toString());
      }
    }
    factory.getCache().evictAll();
  }

  @AfterEach
  void cleanup() {
    jdbc.update("delete from cars where brand='QueryLab'");
    jdbc.update("delete from specifications where name like 'QUERYLAB%'");
    jdbc.update("delete from categories where name like 'QUERYCAT%'");
  }

  @Nested
  class Search {
    @Test
    void givenDistinctLazyCategoriesWhenAccessingEachAfterSearchThenOneExtraQueryPerCarAppears() {
      for (int n = 0; n < ids.size(); n++) {
        var category =
            jdbc.queryForObject(
                "insert into categories(name,description) values (?, 'Query lab') returning id",
                UUID.class,
                "QUERYCAT" + n);
        jdbc.update("update cars set category_id=? where id=?", category, ids.get(n));
      }
      tx.executeWithoutResult(status -> {
        statistics.clear();
        var page =
            cars.search(
                false,
                "%",
                "%",
                "%querylab%",
                "%",
                org.springframework.data.domain.PageRequest.of(
                    0, 6, org.springframework.data.domain.Sort.by("name", "id")));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        for (var car : page) {
          assertThat(factory.getPersistenceUnitUtil().isLoaded(car, "category")).isFalse();
          org.hibernate.Hibernate.initialize(
              org.springframework.test.util.ReflectionTestUtils.getField(car, "category"));
        }
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(9);
      });
    }

    @Test
    void givenTwoPageSizesWhenSerializingSummariesThenQueryCountDoesNotGrowPerCar()
        throws Exception {
      for (int size : List.of(2, 6)) {
        statistics.clear();
        mvc.perform(get("/cars").param("brand", "QueryLab").param("size", String.valueOf(size)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(size))
            .andExpect(jsonPath("$.totalElements").value(8))
            .andExpect(jsonPath("$.content[0].specificationIds.length()").value(2))
            .andExpect(jsonPath("$.content[0].category").doesNotExist())
            .andExpect(jsonPath("$.content[0].specifications").doesNotExist())
            .andExpect(jsonPath("$.content[0].images").doesNotExist());
        assertThat(statistics.getPrepareStatementCount())
            .as("SQL for page size %s", size)
            .isEqualTo(3);
      }
    }

    @Test
    void givenAnIncompleteFirstPageWhenListingThenTheCountQueryCanBeSkipped() throws Exception {
      statistics.clear();
      mvc.perform(get("/cars").param("brand", "QueryLab").param("size", "12"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content.length()").value(8))
          .andExpect(jsonPath("$.totalElements").value(8));
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
  }

  @Nested
  class FindDetailedById {
    @Test
    void givenMultipleImagesAndSpecificationsWhenSerializingDetailThenGraphLoadsWithoutNPlusOne()
        throws Exception {
      statistics.clear();
      mvc.perform(get("/cars/{id}", ids.getFirst()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.category.name").value("SEDAN"))
          .andExpect(jsonPath("$.specifications.length()").value(2))
          .andExpect(jsonPath("$.images.length()").value(3))
          .andExpect(jsonPath("$.specificationIds.length()").value(2));
      assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
  }

  @Nested
  class CollectionPagination {
    @Test
    void givenCollectionRowsWhenPaginatingThenHibernateLimitsCarsInsteadOfJoinedRows() {
      tx.executeWithoutResult(status -> {
        var joinedIds =
            jdbc.queryForList(
                """
                select c.id from cars c join cars_specifications s on s.car_id=c.id
                where c.brand='QueryLab' order by c.name,c.id limit 2
                """,
                UUID.class);
        assertThat(joinedIds).containsExactly(ids.getFirst(), ids.getFirst());
        var page =
            entityManager
                .createQuery(
                    "select c from Car c left join fetch c.specifications where c.brand=:brand order by c.name,c.id",
                    Car.class)
                .setParameter("brand", "QueryLab")
                .setMaxResults(2)
                .getResultList();
        assertThat(page).extracting(Car::id).containsExactly(ids.get(0), ids.get(1));
        assertThat(page)
            .allSatisfy(car ->
                assertThat(factory.getPersistenceUnitUtil().isLoaded(car, "specifications"))
                    .isTrue());
      });
    }
  }
}
