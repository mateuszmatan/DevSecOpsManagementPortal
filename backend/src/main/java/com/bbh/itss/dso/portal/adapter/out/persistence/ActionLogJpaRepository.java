package com.bbh.itss.dso.portal.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActionLogJpaRepository extends JpaRepository<ActionLogEntity, Long> {

    List<ActionLogEntity> findAllByOrderByCreatedAtDescIdDesc();
}
