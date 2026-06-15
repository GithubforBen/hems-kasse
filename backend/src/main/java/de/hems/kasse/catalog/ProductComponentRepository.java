package de.hems.kasse.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductComponentRepository extends JpaRepository<ProductComponent, UUID> {

    @Modifying
    @Query("delete from ProductComponent pc where pc.parentProduct.id in :productIds or pc.componentProduct.id in :productIds")
    void deleteByProductIds(List<UUID> productIds);
}
