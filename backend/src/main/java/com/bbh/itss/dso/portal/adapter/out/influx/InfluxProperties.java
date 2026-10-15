package com.bbh.itss.dso.portal.adapter.out.influx;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.duration;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@ConfigurationProperties("dso.influx")
public record InfluxProperties(
        String url,
        @DefaultValue("DevSecOps") String org,
        @DefaultValue("DORA-metrics") String bucket,
        String token,
        @DefaultValue("365d") String lastRunLookback) {

    public InfluxProperties {
        duration(lastRunLookback);
    }

    public boolean configured() {
        return isNotBlank(url);
    }
}
