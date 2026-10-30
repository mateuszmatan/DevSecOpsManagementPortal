package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.NodeBuildSettings.NONE
import static com.bbh.itss.dso.portal.domain.shared.Sections.reported
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class NodeBuildSettingsSpec extends Specification {

    static final NodeBuildSettings FULL = new NodeBuildSettings(true, 'frontend', ['npm ci', 'npm run build:prod'],
            null, '/opt/node-20', ['NODE_OPTIONS=--max-old-space-size=4096', 'CI=true'], 'https://nexus.bbh.com/npm-group/',
            'npmrc-file', 45, false)

    def "Node.js build settings trim their values, keep every command in its order and keep only a switched off scan"() {
        expect:
        new NodeBuildSettings(null, ' frontend ', [' npm ci ', '', null, 'npm test', 'npm test'], ' ', ' ',
                [' CI=true ', ' '], ' ', ' ', null, true) ==
                new NodeBuildSettings(false, 'frontend', ['npm ci', 'npm test', 'npm test'], null, null, ['CI=true'], null,
                        null, null, null)
        new NodeBuildSettings(*([null] * 10)) == NONE
        NONE == new NodeBuildSettings(false, null, [], null, null, [], null, null, null, null)
        FULL.nexusIqScan() == false
    }

    def "the folder of a Node.js build is written relative to the repository root, and '#directory' is #expected"() {
        expect:
        NodeBuildSettings.builder().enabled(true).directory(directory).build().directory() == expected
        written(NodeBuildSettings.builder().enabled(true).directory(directory).build()).build.node.dir == expected

        where:
        directory      || expected
        './frontend'   || 'frontend'
        'web/ui/'      || 'web/ui'
        '.'            || null
        './'           || null
    }

    def "an enabled Node.js build writes every value it sets under build.node and the Nexus IQ scan only when it is off"() {
        expect:
        written(FULL) == [build: [node: [enabled           : true, dir: 'frontend', commands: ['npm ci', 'npm run build:prod'],
                                         nodePath          : '/opt/node-20',
                                         env               : [NODE_OPTIONS: '--max-old-space-size=4096', CI: 'true'],
                                         registry          : 'https://nexus.bbh.com/npm-group/',
                                         npmrcCredentialsId: 'npmrc-file', timeoutMin: 45, nexusIqScan: false]]]
        written(NodeBuildSettings.builder().enabled(true).installationName('NodeJS-20').nexusIqScan(true).build()) ==
                [build: [node: [enabled: true, installationName: 'NodeJS-20']]]
    }

    def "a Node.js build that is not enabled writes nothing, whatever it holds"() {
        expect:
        written(new NodeBuildSettings(false, 'frontend', ['npm ci'], 'NodeJS-20', null, ['CI=true'], null, null, 30, false)) == [:]
        written(NONE) == [:]
    }

    def "an enabled Node.js build is refused for Flutter and may not name both its installation and its home"() {
        expect:
        reported { FULL.validate(it, GRADLE) } == []
        reported { FULL.validate(it, FLUTTER) }.collect { [it.field, it.message] } == [['enabled',
                'must be off for Flutter: a Flutter build has no Gradle or Maven stage for the Node.js build to run before']]
        reported { both(true).validate(it, MAVEN) }.collect { [it.field, it.message] } ==
                [['nodePath', 'give the Node.js installation or its home on the agent, not both']]
    }

    def "a Node.js build that is not enabled keeps its values without those rules"() {
        expect:
        reported { both(false).validate(it, FLUTTER) } == []
    }

    def "commands and variables that do not fit their column are refused, also while the build is off"() {
        given:
        def commands = (1..9).collect { "npm run step-$it -- ${'x' * 480}".toString() }
        def environment = (1..5).collect { "VARIABLE_$it=${'v' * 480}".toString() }

        expect:
        reported { new NodeBuildSettings(enabled, null, commands, null, null, environment, null, null, null, null)
                .validate(it, GRADLE) }*.field == ['commands', 'environment']

        where:
        enabled << [true, false]
    }

    private static NodeBuildSettings both(boolean enabled) {
        NodeBuildSettings.builder().enabled(enabled).installationName('NodeJS-20').nodePath('/opt/node-20').build()
    }
}
