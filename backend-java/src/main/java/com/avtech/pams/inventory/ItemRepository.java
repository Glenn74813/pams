package com.avtech.pams.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @EntityGraph(attributePaths = "category")
    Optional<Item> findWithCategoryById(Long id);

    /** Locks the item row so concurrent stock changes apply one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> lockById(@Param("id") Long id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Catalogue search. classification: null, "ppe", "semi_expendable" or "consumable";
     * level: null, "out", "low" (at or below the reorder point but not out) or "attention" (both).
     */
    @EntityGraph(attributePaths = "category")
    @Query("""
            select i from Item i
            where (:search is null
                   or lower(i.name) like lower(concat('%', cast(:search as string), '%'))
                   or lower(i.code) like lower(concat('%', cast(:search as string), '%')))
              and (:categoryId is null or i.category.id = :categoryId)
              and (:classification is null
                   or (:classification = 'ppe' and i.ppe = true)
                   or (:classification = 'semi_expendable' and i.semiExpendable = true)
                   or (:classification = 'consumable' and i.ppe = false and i.semiExpendable = false))
              and (:level is null
                   or (:level = 'out' and i.currentStock <= 0)
                   or (:level = 'low' and i.currentStock > 0 and i.currentStock <= i.minStock)
                   or (:level = 'attention' and i.currentStock <= i.minStock))
            """)
    Page<Item> search(@Param("search") String search, @Param("categoryId") Long categoryId,
                      @Param("classification") String classification, @Param("level") String level,
                      Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select i from Item i
            where :search is null
               or lower(i.name) like lower(concat('%', cast(:search as string), '%'))
               or lower(i.code) like lower(concat('%', cast(:search as string), '%'))
            order by i.name
            """)
    List<Item> options(@Param("search") String search);

    /** Codes like PREFIX-00042, to find the next free number. */
    @Query("select i.code from Item i where i.code like concat(:prefix, '-%')")
    List<String> codesWithPrefix(@Param("prefix") String prefix);
}
