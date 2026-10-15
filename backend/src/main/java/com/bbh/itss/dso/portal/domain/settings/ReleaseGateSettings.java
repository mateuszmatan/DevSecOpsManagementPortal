package com.bbh.itss.dso.portal.domain.settings;

import com.bbh.itss.dso.portal.domain.shared.ConfigTree;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;

import java.util.List;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public record ReleaseGateSettings(List<Scanner> scanners, Boolean requireCoverage, String stateFile) {

    public static final String STATE_FILE = "release-gate.json";

    public ReleaseGateSettings {
        scanners = normalize(scanners);
        stateFile = trimToNull(stateFile);
    }

    public static List<Scanner> normalize(List<Scanner> scanners) {
        return emptyIfNull(scanners).stream().distinct().sorted(nullsLast(naturalOrder())).toList();
    }

    public void validate(ValidationProblems problems) {
        problems.require("scanners", scanners,
                "select at least one scanner: without any the library gates on all four");
        if (!STATE_FILE.equals(stateFile)) {
            problems.add("stateFile", "must be " + STATE_FILE
                    + ": the security pipeline archives and the extended pipeline copies only that file");
        }
    }

    public void writeTo(ConfigTree defaults) {
        defaults.set("releaseGate.scanners", scanners.stream().map(Scanner::gateKey).toList())
                .set("releaseGate.requireCoverage", requireCoverage)
                .set("releaseGate.stateFile", stateFile);
    }
}
