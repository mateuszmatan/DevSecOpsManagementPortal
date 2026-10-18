package com.bbh.itss.dso.portal.application.catalog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentUsagePort;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import com.bbh.itss.dso.portal.domain.catalog.CatalogUsage;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;

@UseCase
@RequiredArgsConstructor
public class DepartmentService implements DepartmentsUseCase {

    private final DepartmentRepositoryPort departments;
    private final DepartmentUsagePort usage;

    @Override
    @ReadOnly
    public List<DepartmentView> list() {
        Map<Long, CatalogUsage> perDepartment = usage.perDepartment();
        return departments.findAll().stream()
                .sorted(comparing(Department::name, CASE_INSENSITIVE_ORDER))
                .map(department -> new DepartmentView(department.id(), department.name(), department.version(),
                        perDepartment.getOrDefault(department.id(), CatalogUsage.UNUSED)))
                .toList();
    }

    @Override
    @ReadOnly
    public DepartmentView get(long id) {
        return list().stream().filter(department -> department.id() == id).findFirst()
                .orElseThrow(() -> notFound("Business service", id));
    }

    @Override
    public DepartmentView create(String name) {
        return get(departments.save(Department.create(name, departments::findByName)).id());
    }

    @Override
    public DepartmentView rename(long id, Long version, String name) {
        Department department = departments.load(id).orElseThrow(() -> notFound("Business service", id));
        departments.save(department.rename(version, name, departments::findByName));
        return get(id);
    }

    @Override
    public void delete(long id) {
        DepartmentView department = get(id);
        long products = department.usage().productCount();
        if (products > 0) {
            throw new IllegalStateException(department.name() + " still has " + products
                    + " product(s). Move them to another business service first.");
        }
        departments.delete(id);
    }
}
