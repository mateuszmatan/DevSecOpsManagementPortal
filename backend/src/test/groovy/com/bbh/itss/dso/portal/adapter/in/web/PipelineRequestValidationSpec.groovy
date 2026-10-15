package com.bbh.itss.dso.portal.adapter.in.web

import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import spock.lang.Specification

import static com.bbh.itss.dso.portal.support.Json.parse
import static com.bbh.itss.dso.portal.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class PipelineRequestValidationSpec extends Specification {

    MockMvc mvc = standaloneSetup(new PipelineSampleController()).setControllerAdvice(new ApiExceptionHandler()).build()

    def "the pipeline settings refuse #labels against the field that holds them"() {
        when:
        def response = mvc.perform(post('/api/samples').contentType(APPLICATION_JSON)
                .content(toJson([type: 'FULL', agentLabels: labels]))).andReturn().response

        then:
        response.status == 400
        with(parse(response.contentAsString)) {
            title == 'Validation failed'
            it.detail == detail
            errors*.field == fields
        }

        where:
        labels       || detail                                                                               | fields
        []           || 'add at least one Jenkins agent label'                                               | ['agentLabels']
        ['a', 'b,c'] || 'must be a Jenkins label or label expression such as linux && docker, without commas' | ['agentLabels[1]']
    }

    @RestController
    static class PipelineSampleController {

        @PostMapping('/api/samples')
        String create(@jakarta.validation.Valid @RequestBody PipelineRequest request) {
            request.type().name()
        }
    }
}
