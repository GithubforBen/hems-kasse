package de.hems.kasse.register;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegisterRepository extends JpaRepository<Register, UUID> {
    List<Register> findAllByOrderBySortOrderAsc();

    List<Register> findAllByActiveTrueOrderBySortOrderAsc();
}
