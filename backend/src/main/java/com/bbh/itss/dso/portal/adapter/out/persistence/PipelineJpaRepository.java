package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface PipelineJpaRepository extends JpaRepository<PipelineEntity, Long> {

    @Query("""
            select p from PipelineEntity p join fetch p.service s join fetch s.product pr
            where pr.id = :productId order by s.displayOrder, s.name, p.type""")
    List<PipelineEntity> findByProductId(@Param("productId") Long productId);

    @Query("""
            select p from PipelineEntity p join fetch p.service s join fetch s.product pr
            order by pr.name, s.displayOrder, s.name, p.type""")
    List<PipelineEntity> findAllWithService();

    @Query("""
            select p from PipelineEntity p join fetch p.service s join fetch s.product pr
            where pr.departmentId = :departmentId order by pr.name, s.displayOrder, s.name, p.type""")
    List<PipelineEntity> findByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("select p from PipelineEntity p join fetch p.service s join fetch s.product where p.id = :id")
    Optional<PipelineEntity> findWithServiceById(@Param("id") Long id);

    @Lock(PESSIMISTIC_WRITE)
    @Query("select p from PipelineEntity p where p.id = :id")
    Optional<PipelineEntity> findForUpdate(@Param("id") Long id);

    @Query("""
            select s.settings.metrics.influxProject, s.settings.metrics.influxEnv, p.type, s.id
            from PipelineEntity p join p.service s""")
    List<Object[]> metricsTags();

    boolean existsByServiceIdAndType(Long serviceId, PipelineType type);

    @Query("select p.service.product.id, count(p) from PipelineEntity p group by p.service.product.id")
    List<Object[]> countByProduct();

    @Query("""
            select p.service.product.id, count(distinct p) from PipelineEntity p join p.keys k
            where k.status = com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE
            group by p.service.product.id""")
    List<Object[]> countWithActiveKeyByProduct();

    @Query("""
            select p.service.product.departmentId, count(p) from PipelineEntity p
            where p.service.product.departmentId is not null group by p.service.product.departmentId""")
    List<Object[]> countByDepartment();

    @Query("""
            select p.service.product.departmentId, count(distinct p) from PipelineEntity p join p.keys k
            where k.status = com.bbh.itss.dso.portal.domain.pipeline.KeyStatus.ACTIVE
            and p.service.product.departmentId is not null
            group by p.service.product.departmentId""")
    List<Object[]> countWithActiveKeyByDepartment();
}
