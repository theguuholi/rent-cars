package com.github.theguuholi.rentcars.categories;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Size(max = 50)
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String description;

    @CreationTimestamp
    @Column(name = "inserted_at", nullable = false, updatable = false)
    private Instant insertedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public Category(String name, String description) {
        this.name = normalizeName(name);
        this.description = description;
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Uses a fixed locale so the same name maps to the same unique catalog key
     * regardless of the language settings of the host.
     */
    private static String normalizeName(String name) {
        return name == null ? null : name.toUpperCase(Locale.ROOT);
    }
}
