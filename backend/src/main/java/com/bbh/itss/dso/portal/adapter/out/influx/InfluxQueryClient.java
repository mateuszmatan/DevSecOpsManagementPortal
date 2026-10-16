package com.bbh.itss.dso.portal.adapter.out.influx;

import com.bbh.itss.dso.portal.adapter.MonitoringSettingsStore;
import com.bbh.itss.dso.portal.domain.settings.PlatformSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.duration;
import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.string;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.parseMediaType;

@Component
@Slf4j
public class InfluxQueryClient {

    static final String NOT_CONFIGURED = "InfluxDB is not configured for the portal";
    static final String UNREADABLE = "InfluxDB could not be read: ";
    private static final int MAX_REASON = 300;

    private final InfluxProperties properties;
    private final MonitoringSettingsStore monitoring;
    private final RestClient client;

    public InfluxQueryClient(InfluxProperties properties, RestClient.Builder builder) {
        this(properties, builder, new MonitoringSettingsStore());
    }

    @Autowired
    public InfluxQueryClient(InfluxProperties properties, RestClient.Builder builder,
                             MonitoringSettingsStore monitoring) {
        this.properties = properties;
        this.monitoring = monitoring != null ? monitoring : new MonitoringSettingsStore();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ofSeconds(3));
        requestFactory.setReadTimeout(ofSeconds(20));
        this.client = builder.requestFactory(requestFactory).build();
    }

    public boolean configured() {
        return isNotBlank(url());
    }

    String url() {
        return firstNonBlank(monitoring.platform().map(PlatformSettings::influxUrl).orElse(null), properties.url());
    }

    String org() {
        return firstNonBlank(monitoring.platform().map(PlatformSettings::influxOrg).orElse(null), properties.org());
    }

    String bucket() {
        return string(firstNonBlank(monitoring.platform().map(PlatformSettings::influxBucket).orElse(null),
                properties.bucket()));
    }

    String token() {
        return firstNonBlank(monitoring.platform().map(PlatformSettings::influxToken).orElse(null), properties.token());
    }

    String lastRunLookback() {
        return duration(properties.lastRunLookback());
    }

    void requireConfigured() {
        if (!configured()) {
            throw unavailable(NOT_CONFIGURED, null);
        }
    }

    <T> T read(Supplier<T> reading) {
        requireConfigured();
        try {
            return reading.get();
        } catch (UncheckedIOException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Reading from InfluxDB failed: {}", e.getMessage());
            throw unavailable(UNREADABLE
                    + truncate(getIfNull(e.getMessage(), e.getClass().getSimpleName()), MAX_REASON), e);
        }
    }

    public List<Map<String, String>> query(String flux) {
        requireConfigured();
        URI target = UriComponentsBuilder.fromUriString(url())
                .path("/api/v2/query")
                .queryParam("org", org())
                .build()
                .toUri();
        String csv = client.post()
                .uri(target)
                .header("Authorization", "Token " + defaultString(token()))
                .contentType(APPLICATION_JSON)
                .accept(parseMediaType("application/csv"))
                .body(Map.of("query", flux, "type", "flux",
                        "dialect", Map.of("header", true, "annotations", List.of(), "delimiter", ",")))
                .retrieve()
                .body(String.class);
        return FluxCsv.parse(csv);
    }

    private static UncheckedIOException unavailable(String reason, Throwable cause) {
        return new UncheckedIOException(reason, new IOException(cause));
    }
}

