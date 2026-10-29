package com.bbh.itss.dso.portal.regression

import com.bbh.itss.dso.portal.support.PortalSpecification
import org.yaml.snakeyaml.Yaml

import static com.bbh.itss.dso.portal.support.ApiJson.fullFlutterService
import static com.bbh.itss.dso.portal.support.ApiJson.fullMavenService
import static com.bbh.itss.dso.portal.support.ApiJson.fullOpenShiftService
import static com.bbh.itss.dso.portal.support.ApiJson.product

class ServiceConfigImportRegressionSpec extends PortalSpecification {

    static final String IMPORT = '/api/service-configs/import'

    def "a product's config imported into a new product renders the same projects, and reading it is not recorded"() {
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
        new Yaml().load(api.get("/api/products/$copy.id/config").body) == new Yaml().load(config)
    }

    def "a file that is not a config file is refused at the position of its error, which never echoes the file"() {
        given:
        int actions = actionCount()

        when:
        def malformed = api.post(IMPORT, [content: 'projects:\n  gui:\n    asoc:\n      keySecret: s3cr3t-value\n'
                + '      keyId: [bbh_key\n'])
        def oversized = api.post(IMPORT, [content: 'a: ' + 'x' * 262_144])
        def notJson = api.post(IMPORT, [content: '{"projects": {"gui": {"asoc": "s3cr3t-value"'])

        then:
        [malformed, oversized, notJson]*.status == [400, 400, 400]
        malformed.json.errors == [[field: 'content', message: "line 6, column 1: expected ',' or ']', but got <stream end>"]]
        oversized.json.errors*.field == ['content']
        notJson.json.errors == [[field: 'content', message: 'line 1, column 45: is not valid JSON']]
        [malformed, notJson].every { !it.body.contains('s3cr3t') }
        actionCount() == actions
    }

    private int actionCount() {
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_ACTION_LOG', Integer)
    }
}
