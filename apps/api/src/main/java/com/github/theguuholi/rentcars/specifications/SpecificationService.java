package com.github.theguuholi.rentcars.specifications;

import com.github.theguuholi.rentcars.shared.http.EditVersion;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class SpecificationService {
  private final SpecificationRepository repository;
  private final Validator validator;

  SpecificationService(SpecificationRepository repository, Validator validator) {
    this.repository = repository;
    this.validator = validator;
  }

  List<Specification> list() {
    return repository.findAll(Sort.by("name"));
  }

  Specification get(UUID id) {
    return repository.findById(id).orElseThrow(SpecificationService::notFound);
  }

  @Transactional
  Specification create(SpecificationRequest attributes) {
    return save(new Specification(), attributes);
  }

  @Transactional
  Specification update(UUID id, SpecificationRequest attributes) {
    var specification = repository.findById(id).orElseThrow(SpecificationService::notFound);
    EditVersion.requireCurrent(attributes.expectedVersion, specification.getVersion());
    return save(specification, attributes);
  }

  @Transactional
  void delete(UUID id) {
    var item = repository.findForUpdate(id).orElseThrow(SpecificationService::notFound);
    repository.advanceLinkedCarVersions(id);
    repository.delete(item);
  }

  private Specification save(Specification specification, SpecificationRequest attributes) {
    specification.apply(attributes);
    var errors = validator.validate(specification);
    if (!errors.isEmpty()) throw new ConstraintViolationException(errors);
    return repository.saveAndFlush(specification);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Specification not found");
  }
}
