package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification

import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.ApiJson.service
import static com.bbh.itss.dso.portal.support.CatalogFixtures.DEPARTMENT_ID
import static java.lang.String.CASE_INSENSITIVE_ORDER

class DepartmentRegressionSpec extends PortalSpecification {

    def "the five BBH departments are there from the start and every department is listed by name in any case"() {
        given:
        createDepartment('aaa lower case ' + uniqueCode())

        when:
        def departments = api.get('/api/departments').json

        then:
        departments*.name.containsAll(['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services'])
        departments*.name == departments*.name.sort(false, CASE_INSENSITIVE_ORDER)
        departments.find { it.id == DEPARTMENT_ID }.name == 'Corporate Technology'
        departments.every {
            it.keySet() as List == ['id', 'name', 'version', 'productCount', 'serviceCount', 'pipelineCount',
                                    'activePipelineCount']
        }
    }

    def "a department is created, renamed at the version it was read at and deleted"() {
        given:
        def name = "Treasury ${uniqueCode()}".toString()

        when:
        def created = api.post('/api/departments', [name: " $name "])

        then:
        created.status == 201
        created.header('Location') == "$api.baseUrl/api/departments/${created.json.id}"
        created.json == [id          : created.json.id, name: name, version: 0, productCount: 0, serviceCount: 0,
                         pipelineCount: 0, activePipelineCount: 0]
        api.get('/api/departments').json.find { it.id == created.json.id } == created.json

        when:
        def renamed = api.put("/api/departments/$created.json.id", [name: "$name Services", version: 0])
        def stale = api.put("/api/departments/$created.json.id", [name: "$name Again", version: 0])
        def unversioned = api.put("/api/departments/$created.json.id", [name: "$name Again"])

        then:
        renamed.status == 200
        renamed.json == created.json + [name: "$name Services".toString(), version: 1]
        [stale, unversioned]*.status == [409, 409]
        [stale, unversioned].every { it.json.detail.contains('changed by someone else') }
        api.get('/api/departments').json.find { it.id == created.json.id }.name == "$name Services".toString()

        when:
        def deleted = api.delete("/api/departments/$created.json.id")

        then:
        deleted.status == 204
        api.get('/api/departments').json.every { it.id != created.json.id }
        api.delete("/api/departments/$created.json.id").status == 404
    }

    def "a department counts its products, their services, their DevSecOps pipelines and the active ones"() {
        given:
        def department = createDepartment()
        def ledger = createProduct(product(code: uniqueCode(), name: "Ledger ${uniqueCode()}", departmentId: department.id,
                services: [service(name: 'gui'), service(name: 'api')]))
        def payments = createProduct(product(code: uniqueCode(), name: "Payments ${uniqueCode()}",
                departmentId: department.id))
        createPipeline(ledger.services[0].id as long, [type: 'SAST', agentLabels: ['linux-agent']])
        def retired = pipelineOfService(payments.services[0].id as long)
        api.post("/api/pipelines/$retired.id/keys/revoke", [reason: 'Retired'])

        when:
        def counted = api.get('/api/departments').json.find { it.id == department.id }
        def listed = api.get('/api/products').json.findAll { it.departmentId == department.id }

        then:
        counted == department + [productCount: 2, serviceCount: 3, pipelineCount: 4, activePipelineCount: 3]
        listed*.id as Set == [ledger.id, payments.id] as Set
        listed*.departmentName.unique() == [department.name]
        listed[0].keySet() as List == ['id', 'code', 'name', 'description', 'ownerTeam', 'departmentId', 'departmentName',
                                       'serviceCount', 'pipelineCount', 'activePipelineCount', 'updatedAt']
        api.get("/api/products?search=${URLEncoder.encode(department.name.toUpperCase(), 'UTF-8')}").json*.id as Set ==
                [ledger.id, payments.id] as Set
        api.get("/api/products/$ledger.id").json.keySet() as List ==
                ['id', 'code', 'name', 'description', 'ownerTeam', 'contactEmail', 'departmentId', 'appScan', 'version',
                 'createdAt', 'updatedAt', 'services']
    }

    def "a department with products is deleted only once they moved to another department"() {
        given:
        def department = createDepartment()
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code", departmentId: department.id))

        when:
        def refused = api.delete("/api/departments/$department.id")

        then:
        refused.status == 409
        refused.json.detail == "$department.name still has 1 product(s). Move them to another department first."

        when:
        def moved = api.put("/api/products/$created.id", product(code: code, name: "Product $code",
                version: created.version, services: created.services, departmentId: DEPARTMENT_ID))

        then:
        moved.status == 200
        moved.json.departmentId == DEPARTMENT_ID
        api.delete("/api/departments/$department.id").status == 204
    }

    def "department names are unique in any case, also on rename"() {
        given:
        def first = createDepartment()
        def second = createDepartment()

        when:
        def created = api.post('/api/departments', [name: first.name.toUpperCase()])
        def renamed = api.put("/api/departments/$second.id", [name: first.name.toLowerCase(), version: second.version])
        def ownName = api.put("/api/departments/$first.id", [name: first.name.toUpperCase(), version: first.version])

        then:
        [created, renamed]*.status == [409, 409]
        [created, renamed]*.json*.detail == ["A department named $first.name already exists".toString()] * 2
        ownName.status == 200
        ownName.json.name == first.name.toUpperCase()
    }

    def "a department name that is #problem is refused against its field"() {
        when:
        def response = api.post('/api/departments', [name: name])

        then:
        response.status == 400
        response.json.errors*.field == ['name']

        where:
        problem                       | name
        'missing'                     | null
        'blank'                       | '   '
        'too long'                    | 'D' * 101
        'longer than 100 UTF-8 bytes' | 'Księgowość ' * 9
    }

    def "an unknown department is not found"() {
        when:
        def renamed = api.put('/api/departments/99999', [name: 'Nowhere', version: 0])
        def deleted = api.delete('/api/departments/99999')

        then:
        [renamed, deleted]*.status == [404, 404]
        deleted.json.detail == 'Department 99999 does not exist'
    }

    def "a product is saved only in a department that exists: #problem"() {
        given:
        def code = uniqueCode()
        def stored = createProduct(product(code: code, name: "Product $code"))

        when:
        def created = api.post('/api/products', product(code: uniqueCode(), name: "Other ${uniqueCode()}",
                departmentId: departmentId))
        def updated = api.put("/api/products/$stored.id", product(code: code, name: "Product $code",
                version: stored.version, services: stored.services, departmentId: departmentId))

        then:
        [created, updated]*.status == [400, 400]
        [created, updated]*.json*.errors == [[[field: 'departmentId', message: message]]] * 2
        api.get("/api/products/$stored.id").json.departmentId == DEPARTMENT_ID

        where:
        problem                   | departmentId || message
        'none chosen'             | null         || "choose the product's department"
        'one that does not exist' | 99999        || 'department 99999 does not exist'
    }

    def "a product from before departments shows without one and counts in none"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "Product $code"))
        def before = api.get('/api/departments').json.find { it.id == DEPARTMENT_ID }

        when:
        jdbc.update('UPDATE DSO_PRODUCT SET DEPARTMENT_ID = NULL WHERE ID = ?', created.id)
        def listed = api.get('/api/products').json.find { it.id == created.id }

        then:
        [listed.departmentId, listed.departmentName] == [null, null]
        api.get("/api/products/$created.id").json.departmentId == null
        api.get('/api/departments').json.find { it.id == DEPARTMENT_ID }.productCount == before.productCount - 1
    }

    private Map createDepartment(String name = "Department ${uniqueCode()}") {
        def response = api.post('/api/departments', [name: name])
        assert response.status == 201: response
        response.json as Map
    }
}
