package com.github.theguuholi.rentcars;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@io.swagger.v3.oas.annotations.OpenAPIDefinition(
    info =
        @io.swagger.v3.oas.annotations.info.Info(
            title = "Rent Cars API",
            version = "0.0.1-SNAPSHOT",
            description =
                "Rent Cars is a car rental project. Its first resource is the vehicle category catalog. "
                    + "Use GET /categories to show drivers the body styles in the catalog and explain each option."))
@SpringBootApplication
public class RentCarsApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(RentCarsApiApplication.class, args);
  }
}
