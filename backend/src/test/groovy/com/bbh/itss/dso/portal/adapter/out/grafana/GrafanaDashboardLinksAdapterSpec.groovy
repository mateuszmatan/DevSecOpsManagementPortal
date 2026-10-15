package com.bbh.itss.dso.portal.adapter.out.grafana

import com.bbh.itss.dso.portal.domain.monitoring.DashboardLink
import com.bbh.itss.dso.portal.domain.monitoring.MetricsTag
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.EXTENDED
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.FULL
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.NEXUS_IQ
import static com.bbh.itss.dso.portal.domain.pipeline.PipelineType.SAST

class GrafanaDashboardLinksAdapterSpec extends Specification {

    static final String PIPELINE = 'https://grafana.bbh.com/d/adzfc54123/devsecops-pipeline-long'
    static final String SECURITY = 'https://grafana.bbh.com/d/ad2trcm/devsecops-security?orgId=2'
    static final String PROD = 'https://grafana-prod.bbh.com/d/adzfc54123/devsecops-pipeline-long'

    def tag = new MetricsTag('CERT gui', 'test')

    def "the #type pipeline links #dashboard of every Grafana instance with its project and range"() {
        given:
        def adapter = new GrafanaDashboardLinksAdapter(properties([' Test ', " $PIPELINE ", security], [null, PROD, '']))

        expect:
        adapter.instances() == [new DashboardLink('Test', PIPELINE), new DashboardLink('Grafana', PROD)]
        adapter.dashboards(tag, type, 90) == [new DashboardLink('Test', link.toString()),
                                              new DashboardLink('Grafana', "$PROD?var-project=CERT%20gui&from=now-90d&to=now")]

        where:
        type                  | security || dashboard          | link
        FULL                  | SECURITY || 'the pipeline one' | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
        EXTENDED              | SECURITY || 'the pipeline one' | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
        PipelineType.SECURITY | SECURITY || 'the security one' | "$SECURITY&var-project=CERT%20gui&from=now-90d&to=now"
        SAST                  | SECURITY || 'the security one' | "$SECURITY&var-project=CERT%20gui&from=now-90d&to=now"
        SAST                  | ' '      || 'the pipeline one' | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
        NEXUS_IQ              | SECURITY || 'the security one' | "$SECURITY&var-project=CERT%20gui&from=now-90d&to=now"
        NEXUS_IQ              | null     || 'the pipeline one' | "$PIPELINE?var-project=CERT%20gui&from=now-90d&to=now"
    }

    def "an instance with the security dashboard only links security pipelines, and one without a dashboard does not exist"() {
        given:
        def adapter = new GrafanaDashboardLinksAdapter(properties(['Security', null, SECURITY], ['Unused', ' ', null]))

        expect:
        adapter.instances() == [new DashboardLink('Security', SECURITY)]
        adapter.dashboards(tag, FULL, 30) == []
        adapter.dashboards(tag, SAST, 7) == [new DashboardLink('Security', "$SECURITY&var-project=CERT%20gui&from=now-7d&to=now")]
        new GrafanaDashboardLinksAdapter(new GrafanaProperties(null)).instances() == []
        new GrafanaDashboardLinksAdapter(new GrafanaProperties([])).dashboards(tag, FULL, 30) == []
    }

    static GrafanaProperties properties(List<String>... instances) {
        new GrafanaProperties(instances.collect { new GrafanaProperties.Instance(it[0], it[1], it[2]) })
    }
}
