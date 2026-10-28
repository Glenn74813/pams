package com.avtech.pams.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByParentId(Long parentId);

    boolean existsByNameIgnoreCaseAndParentId(String name, Long parentId);

    boolean existsByNameIgnoreCaseAndParentIdIsNull(String name);

    /** [categoryId, itemCount] for every category that has items. */
    @Query("select i.category.id, count(i) from Item i group by i.category.id")
    List<Object[]> itemCounts();
}
