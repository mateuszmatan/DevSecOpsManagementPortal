package com.bbh.itss.dso.portal.support

/** A rendered config as the DSOEnhanced library reads it. */
final class LibraryConfig {

    private LibraryConfig() {
    }

    /** The settings each project runs with: the defaults section deep-merged with the project's own values over it. */
    static Map projectsAsRun(Map config, Map defaults) {
        (config.projects as Map).collectEntries { name, project -> [name, merged(defaults, project as Map)] }
    }

    private static Map merged(Map base, Map own) {
        (base.keySet() + own.keySet()).collectEntries { key ->
            [key, base[key] instanceof Map && own[key] instanceof Map ? merged(base[key] as Map, own[key] as Map)
                    : own.containsKey(key) ? own[key] : base[key]]
        }
    }
}
