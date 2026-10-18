package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessServiceJpaRepository extends JpaRepository<BusinessServiceEntity, Long> {

    Optional<BusinessServiceEntity> findByNameIgnoreCase(String name);
}
