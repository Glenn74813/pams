package com.avtech.pams.physicalcount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CountLineRepository extends JpaRepository<CountLine, Long> {
    List<CountLine> findByCountIdOrderById(Long countId);

    Optional<CountLine> findByIdAndCountId(Long id, Long countId);
}
