package com.bbh.itss.dso.portal.domain.catalog;

import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_2000;
import static com.bbh.itss.dso.portal.domain.shared.Text.clean;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record NexusIqApplication(String application, List<String> scanPatterns, String stage,
                                 Boolean failOnNetworkError) {

    public static final String DEFAULT_STAGE = "build";

    public NexusIqApplication {
        application = trimToNull(application);
        scanPatterns = clean(scanPatterns);
        stage = defaultIfBlank(trim(stage), DEFAULT_STAGE);
        failOnNetworkError = isTrue(failOnNetworkError);
    }

    public static NexusIqApplication of(String application, List<String> scanPatterns) {
        return new NexusIqApplication(application, scanPatterns, null, false);
    }

    public void validate(ValidationProblems problems) {
        problems.require("application", application, "must not be blank")
                .require("scanPatterns", scanPatterns, "add at least one scan pattern for the Nexus IQ application");
        LINES_2000.check(problems, "scanPatterns", scanPatterns);
    }
}
