package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.Builder;

@Builder
public record ScanSettings(Integer coverageMinLine, Integer sastPrepareTimeoutMinutes, Integer sastPollTimeoutMinutes,
                           Integer sastPollIntervalSeconds, Integer dastPollTimeoutMinutes,
                           Integer dastPollIntervalSeconds, Integer dastReportTimeoutMinutes,
                           Integer dastReportIntervalSeconds, Boolean sonarWaitForQualityGate,
                           Integer sonarQualityGateTimeoutMinutes) {

    public void validate(ValidationProblems problems) {
        if (coverageMinLine != null && coverageMinLine < 1) {
            problems.add("coverageMinLine", "must be at least 1: the library replaces 0 with 60; turn off the coverage requirement of the release gate instead");
        }
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("coverage.minLine", coverageMinLine)
                .set("tools.sonar.qualityGate.waitForQualityGate", sonarWaitForQualityGate)
                .set("tools.sonar.qualityGate.timeoutMinutes", sonarQualityGateTimeoutMinutes)
                .set("sast.prepareTimeoutMin", sastPrepareTimeoutMinutes)
                .set("sast.pollTimeoutMin", sastPollTimeoutMinutes)
                .set("sast.pollIntervalSec", sastPollIntervalSeconds)
                .set("dast.pollTimeoutMin", dastPollTimeoutMinutes)
                .set("dast.pollIntervalSec", dastPollIntervalSeconds)
                .set("dast.reportTimeoutMin", dastReportTimeoutMinutes)
                .set("dast.reportIntervalSec", dastReportIntervalSeconds);
    }
}
