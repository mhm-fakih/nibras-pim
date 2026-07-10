package com.nibras.pim.repo;

import com.nibras.pim.domain.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@NoRepositoryBean
public interface BaseRepository<E extends BaseEntity>
        extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {

    /** Per-day creation counts for the mini-calendar. Returns rows of [java.sql.Date, Long]. */
    @Query("""
            select function('date', e.dateCreated), count(e)
            from #{#entityName} e
            where e.dateCreated between :from and :to
            group by function('date', e.dateCreated)
            """)
    List<Object[]> countByCreationDay(@Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);
}
