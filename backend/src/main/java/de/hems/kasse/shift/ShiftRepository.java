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

    List<Shift> findAllBySubjectKeyAndClosedAtIsNotNullOrderByClosedAtDesc(String subjectKey);

    @Query("""
        select s from Shift s
        where s.closedAt is not null
          and s.closedAt >= :from
          and s.closedAt <  :to
          and (:klasse is null or lower(s.klasse) = lower(cast(:klasse as string)))
          and (:q is null or lower(s.userName) like lower(concat('%', cast(:q as string), '%')))
        order by s.closedAt desc
        """)
    List<Shift> searchClosed(@Param("from") Instant from,
                             @Param("to") Instant to,
                             @Param("klasse") String klasse,
                             @Param("q") String q);
}
