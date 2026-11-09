package com.github.theguuholi.rentcars;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.github.theguuholi.rentcars",
    importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {
  private static final String ROOT = "com.github.theguuholi.rentcars";

  @ArchTest
  static void namedConceptsBelongToTheirDomains(JavaClasses importedClasses) {
    conceptIn("Car", "cars", importedClasses);
    conceptIn("CarImage", "cars", importedClasses);
    conceptIn("Rental", "rentals", importedClasses);
    conceptIn("Category", "categories", importedClasses);
    conceptIn("Specification", "specifications", importedClasses);
    conceptIn("Account", "auth", importedClasses);
  }

  private static void conceptIn(String name, String domain, JavaClasses importedClasses) {
    String expectedName = ROOT + "." + domain + "." + name;
    assertTrue(
        importedClasses.stream().anyMatch(type -> type.getName().equals(expectedName)),
        () -> "Missing business concept: " + expectedName);
    classes()
        .that()
        .haveSimpleName(name)
        .should()
        .resideInAPackage(ROOT + "." + domain)
        .because("each business concept is owned by its domain")
        .check(importedClasses);
  }

  @ArchTest
  static final ArchRule rentalsDoNotAccessCarPersistence =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".rentals")
          .should()
          .dependOnClassesThat()
          .haveSimpleName("CarRepository");

  @ArchTest
  static final ArchRule carsDoNotDependOnRentals =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".cars")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + ".rentals")
          .because("rental orchestration owns the car rental lifecycle");

  @ArchTest
  static final ArchRule catalogsAndAuthDoNotDependOnRentalInternals =
      noClasses()
          .that()
          .resideInAnyPackage(ROOT + ".categories", ROOT + ".specifications", ROOT + ".auth")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + ".rentals")
          .because("catalogs and authentication are independent of rental orchestration");
}
