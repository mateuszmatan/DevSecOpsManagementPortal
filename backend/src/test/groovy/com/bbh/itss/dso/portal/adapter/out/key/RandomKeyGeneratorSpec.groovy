package com.bbh.itss.dso.portal.adapter.out.key

import spock.lang.Specification
import spock.lang.Subject

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineKey.normalize

class RandomKeyGeneratorSpec extends Specification {

    @Subject
    RandomKeyGenerator generator = new RandomKeyGenerator()

    def "a key is a random UUID in its canonical lower-case form"() {
        when:
        def key = generator.newKey()

        then:
        key ==~ /[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/
        normalize(key) == key
    }

    def "every key differs from the keys issued before"() {
        expect:
        (1..200).collect { generator.newKey() }.toSet().size() == 200
    }
}
