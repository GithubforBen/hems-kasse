package de.hems.kasse.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {
    Optional<InventoryCount> findFirstByOrderByTsDesc();
    List<InventoryCount> findAllByOrderByTsDesc();
}
