package com.avtech.pams.bac;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BacMemberRepository extends JpaRepository<BacMember, Long> {

    List<BacMember> findAllByOrderBySequenceAscIdAsc();

    List<BacMember> findByActiveTrue();
}
