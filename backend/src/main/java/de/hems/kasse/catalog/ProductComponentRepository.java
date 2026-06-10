package de.hems.kasse.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductComponentRepository extends JpaRepository<ProductComponent, UUID> {
}
