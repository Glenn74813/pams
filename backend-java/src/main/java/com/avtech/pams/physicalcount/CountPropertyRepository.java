package com.avtech.pams.physicalcount;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CountPropertyRepository extends JpaRepository<CountProperty, Long> {
    List<CountProperty> findByCountIdOrderByTagNumber(Long countId);

    Optional<CountProperty> findByCountIdAndTagNumberIgnoreCase(Long countId, String tag);

    Optional<CountProperty> findByIdAndCountId(Long id, Long countId);
}
