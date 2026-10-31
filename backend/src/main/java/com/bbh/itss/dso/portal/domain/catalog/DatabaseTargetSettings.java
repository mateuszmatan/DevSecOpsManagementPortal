package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import lombok.Builder;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.catalog.DatabaseSettings.unlessDefault;
import static com.bbh.itss.dso.portal.domain.catalog.DatabaseTarget.TEST;
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.environmentMap;
import static com.bbh.itss.dso.portal.domain.shared.Text.trimmed;
import static org.apache.commons.lang3.ObjectUtils.allNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/** A database the pipelines deploy the changes of a SQL and PL/SQL service to; the user stays in Jenkins. */
@Builder
public record DatabaseTargetSettings(String url, String credentialsId, String contexts, String labels,
                                     String defaultSchema, String liquibaseSchema, List<String> parameters,
                                     Integer timeoutMinutes) {

    public DatabaseTargetSettings {
        url = trimToNull(url);
        credentialsId = trimToNull(credentialsId);
        contexts = trimToNull(contexts);
        labels = trimToNull(labels);
        defaultSchema = trimToNull(defaultSchema);
        liquibaseSchema = trimToNull(liquibaseSchema);
        parameters = trimmed(parameters);
    }

    /** The same database without the values the library uses anyway: test for the test database and 30 minutes. */
    public DatabaseTargetSettings withoutDefaults(DatabaseTarget target) {
        return new DatabaseTargetSettings(url, credentialsId, target == TEST ? unlessDefault(contexts, "test") : contexts,
                labels, defaultSchema, liquibaseSchema, parameters, unlessDefault(timeoutMinutes, 30));
    }

    public boolean isEmpty() {
        return allNull(url, credentialsId, contexts, labels, defaultSchema, liquibaseSchema, timeoutMinutes)
                && parameters.isEmpty();
    }

    public Map<String, Object> toConfig() {
        return new ConfigTree()
                .set("url", url)
                .set("credentialsId", credentialsId)
                .set("contexts", contexts)
                .set("labels", labels)
                .set("defaultSchema", defaultSchema)
                .set("liquibaseSchema", liquibaseSchema)
                .set("parameters", environmentMap(parameters))
                .set("timeoutMinutes", timeoutMinutes)
                .toMap();
    }
}
