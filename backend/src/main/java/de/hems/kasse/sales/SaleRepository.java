package de.hems.kasse.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
    List<Sale> findAllByShiftIdOrderByTsDesc(UUID shiftId);

    List<Sale> findAllByTsBetweenOrderByTsAsc(Instant from, Instant to);
}
