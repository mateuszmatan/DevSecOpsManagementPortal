package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification
import org.yaml.snakeyaml.Yaml

import static com.bbh.itss.dso.portal.support.ApiJson.fullFlutterService
import static com.bbh.itss.dso.portal.support.ApiJson.fullMavenService
import static com.bbh.itss.dso.portal.support.ApiJson.fullOpenShiftService
import static com.bbh.itss.dso.portal.support.ApiJson.product
import static com.bbh.itss.dso.portal.support.LibraryConfig.projectsAsRun

class ServiceConfigImportRegressionSpec extends PortalSpecification {

    static final String IMPORT = '/api/service-configs/import'

    def "a product's config imported into a new product runs the same projects, and reading it is not recorded"() {
        given:
        def code = uniqueCode('IMPORT')
        def original = createProduct(product(code: code, name: "Product $code",
                services: [fullMavenService(), fullOpenShiftService(), fullFlutterService()]))
        String config = api.get("/api/products/$original.id/config").body
        int actions = actionCount()

        when:
        def imported = api.post(IMPORT, [content: config])

        then:
        imported.status == 200
        imported.json.services*.name == original.services*.name
        imported.json.services*.notes == [[], [], []]
        imported.json.pipeline == null
        imported.json.notes == []
        actionCount() == actions

        when:
        def copy = createProduct(product(code: code + 'C', name: "Copy of $code",
                services: imported.json.services*.service))

        then:
        asRun(api.get("/api/products/$copy.id/config").body) == asRun(config)
    }

    def "a file that is not a config file, describes no project or refers to itself is refused without echoing the file"() {
        given:
        int actions = actionCount()

        when:
        def malformed = api.post(IMPORT, [content: 'projects:\n  gui:\n    asoc:\n      keySecret: s3cr3t-value\n'
                + '      keyId: [bbh_key\n'])
        def oversized = api.post(IMPORT, [content: 'a: ' + 'x' * 262_144])
        def notJson = api.post(IMPORT, [content: '{"projects": {"gui": {"asoc": "s3cr3t-value"'])
        def compose = api.post(IMPORT, [content: 'version: "3.9"\nservices:\n  web:\n    image: nginx\n'])
        def recursive = api.post(IMPORT, [content: 'projects:\n  svc: &x\n    buildTool: gradle\n    custom: *x\n'])

        then:
        [malformed, oversized, notJson, compose, recursive]*.status == [400, 400, 400, 400, 400]
        malformed.json.errors == [[field: 'content', message: 'line 6, column 1: is not valid YAML']]
        oversized.json.errors*.field == ['content']
        notJson.json.errors == [[field: 'content', message: 'line 1, column 45: is not valid JSON']]
        compose.json.errors*.field == ['content']
        recursive.json.errors == [[field: 'content', message: 'holds an alias inside the value it refers to']]
        [malformed, notJson].every { !it.body.contains('s3cr3t') }
        actionCount() == actions
    }

    private Map asRun(String config) {
        Map global = new Yaml().load(api.get('/api/settings/config').body) as Map
        projectsAsRun(new Yaml().load(config) as Map, global.defaults as Map)
    }

    private int actionCount() {
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_ACTION_LOG', Integer)
    }
}
