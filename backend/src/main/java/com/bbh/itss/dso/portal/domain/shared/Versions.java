package com.bbh.itss.dso.portal.domain.shared;

import lombok.NoArgsConstructor;

import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;
import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public final class Versions {

    /** A change must say the version it was read at, and that must still be the current one. */
    public static void requireReadAt(Long expected, long current) {
        if (expected == null || expected != current) {
            throw staleVersion();
        }
    }
}
