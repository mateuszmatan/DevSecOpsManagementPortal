package com.bbh.itss.dso.portal.domain.shared

import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem

final class Sections {

    private Sections() {
    }

    static Map written(Object section) {
        def tree = new ConfigTree()
        section instanceof Closure ? section(tree) : section.writeTo(tree)
        tree.toMap()
    }

    static List<FieldProblem> reported(Object section) {
        def problems = new ValidationProblems()
        section instanceof Closure ? section(problems) : section.validate(problems)
        problems.list()
    }

    static List<String> problems(Object section) {
        reported(section)*.field
    }

    static List<String> messages(Object section) {
        reported(section)*.message
    }
}
