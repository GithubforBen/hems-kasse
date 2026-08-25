package de.hems.kasse.shift;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    Optional<Shift> findFirstBySubjectKeyAndClosedAtIsNull(String subjectKey);

    Optional<Shift> findFirstBySubjectKeyAndRegisterIdAndClosedAtIsNull(String subjectKey, UUID registerId);

    boolean existsByRegisterIdAndClosedAtIsNull(UUID registerId);

    Optional<Shift> findByAbrechnungNr(Integer abrechnungNr);

    List<Shift> findAllBySubjectKeyAndClosedAtIsNotNullOrderByClosedAtDesc(String subjectKey);

    @Query("""
        select s from Shift s
        where s.closedAt is not null
          and s.closedAt >= :from
          and s.closedAt <  :to
          and (:gruppe is null or lower(s.gruppe) = lower(cast(:gruppe as string)))
          and (:abrechnungNr is null or s.abrechnungNr = :abrechnungNr)
          and (:registerId is null or s.registerId = :registerId)
          and (:q is null or lower(s.userName) like lower(concat('%', cast(:q as string), '%')) escape '!')
        order by s.closedAt desc
        """)
    List<Shift> searchClosed(@Param("from") Instant from,
                             @Param("to") Instant to,
                             @Param("gruppe") String gruppe,
                             @Param("abrechnungNr") Integer abrechnungNr,
                             @Param("registerId") UUID registerId,
                             @Param("q") String q);
}
