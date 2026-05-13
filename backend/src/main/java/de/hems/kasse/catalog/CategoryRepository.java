package de.hems.kasse.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @EntityGraph(attributePaths = "products")
    List<Category> findAllByOrderBySortOrderAsc();
}
