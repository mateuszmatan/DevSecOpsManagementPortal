package com.bbh.itss.dso.portal.adapter.out.influx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.duration;
import static com.bbh.itss.dso.portal.adapter.out.influx.Flux.string;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.defaultString;
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
    private final RestClient client;

    public InfluxQueryClient(InfluxProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ofSeconds(3));
        requestFactory.setReadTimeout(ofSeconds(20));
        this.client = properties.configured()
                ? builder.baseUrl(properties.url()).requestFactory(requestFactory).build()
                : null;
    }

    public boolean configured() {
        return client != null;
    }

    String bucket() {
        return string(properties.bucket());
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
        String csv = client.post()
                .uri(uri -> uri.path("/api/v2/query").queryParam("org", properties.org()).build())
                .header("Authorization", "Token " + defaultString(properties.token()))
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
