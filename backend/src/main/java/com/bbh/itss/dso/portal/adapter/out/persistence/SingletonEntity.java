package com.bbh.itss.dso.portal.adapter.out.persistence;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.domain.Persistable;

import java.util.Objects;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;

@MappedSuperclass
public abstract class SingletonEntity extends AuditedEntity implements Persistable<Long> {

    static final long ID = 1L;

    @Id
    private Long id = ID;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return createdAt() == null;
    }

    void requireVersion(Long expected) {
        if (!Objects.equals(isNew() ? null : version(), expected)) {
            throw staleVersion();
        }
    }
}
