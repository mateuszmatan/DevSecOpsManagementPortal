package com.bbh.itss.dso.portal.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.FLUTTER
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.GRADLE
import static com.bbh.itss.dso.portal.domain.catalog.BuildTool.MAVEN
import static com.bbh.itss.dso.portal.domain.catalog.ToolCommand.NONE
import static com.bbh.itss.dso.portal.domain.shared.Sections.written

class ToolCommandSpec extends Specification {

    static final ToolCommand FULL = ToolCommand.builder().tasks(['clean', 'build']).flags(['--no-daemon', '-x', 'test'])
            .directory('gui').mavenHome('/opt/maven-3.9').environment(['JAVA_OPTS=-Xmx2g', 'CI=true']).build()

    def "a command trims its values, keeps repeated tokens in their order and may be left out entirely"() {
        expect:
        new ToolCommand([' clean ', '', null, 'build', 'clean'], [' -s ', 'settings.xml', ' -gs ', 'settings.xml', ' '],
                ' gui ', ' ', [' CI=true ', ' '], null, false) ==
                new ToolCommand(['clean', 'build', 'clean'], ['-s', 'settings.xml', '-gs', 'settings.xml'], 'gui', null, ['CI=true'], null, false)
        new ToolCommand(null, null, null, null, null, null, false) == NONE
        NONE == new ToolCommand([], [], null, null, [], null, false)
        ToolCommand.of(['test', ' jacocoTestReport '], ['--info']) ==
                new ToolCommand(['test', 'jacocoTestReport'], ['--info'], null, null, [], null, false)
        ToolCommand.of(null, null) == NONE
    }

    def "a command is empty only when it sets nothing: #description"() {
        expect:
        command.isEmpty() == empty

        where:
        description              | command                                                      || empty
        'nothing at all'         | NONE                                                         || true
        'blank values only'      | new ToolCommand([' '], [''], ' ', ' ', [' '], null, false)   || true
        'tasks'                  | ToolCommand.of(['build'], [])                                || false
        'flags'                  | ToolCommand.of([], ['--offline'])                            || false
        'a directory'            | ToolCommand.builder().directory('gui').build()               || false
        'a Maven installation'   | ToolCommand.builder().mavenHome('/opt/maven').build()        || false
        'an environment'         | ToolCommand.builder().environment(['CI=true']).build()       || false
        'a step label'           | ToolCommand.builder().label('Build').build()                 || false
        'the captured output'    | ToolCommand.builder().returnStdout(true).build()             || false
    }

    def "a #tool command is written as #config"() {
        expect:
        written { command.writeTo(it, path, tool) } == config

        where:
        command                                                                                         | path              | tool    || config
        FULL                                                                                            | 'build'           | GRADLE  || [build: [gradle: [tasks: ['clean', 'build'], flags: ['--no-daemon', '-x', 'test'], dir: 'gui', env: [JAVA_OPTS: '-Xmx2g', CI: 'true']]]]
        FULL                                                                                            | 'tools.sonar'     | MAVEN   || [tools: [sonar: [maven: [goals: ['clean', 'build'], flags: ['--no-daemon', '-x', 'test'], dir: 'gui', mvnPath: '/opt/maven-3.9', env: [JAVA_OPTS: '-Xmx2g', CI: 'true']]]]]
        FULL                                                                                            | 'build'           | FLUTTER || [:]
        NONE                                                                                            | 'build'           | GRADLE  || [:]
        NONE                                                                                            | 'build'           | MAVEN   || [:]
        ToolCommand.builder().tasks(['test']).mavenHome('/opt/maven').environment(['NO_VALUE']).build() | 'tests.unitTests' | MAVEN   || [tests: [unitTests: [maven: [goals: ['test'], mvnPath: '/opt/maven']]]]
        ToolCommand.builder().tasks(['build']).environment(['JUST_A_NAME']).build()                     | 'build'           | GRADLE  || [build: [gradle: [tasks: ['build']]]]
        ToolCommand.builder().tasks(['build']).label(' Build ').returnStdout(true).build()              | 'build'           | GRADLE  || [build: [gradle: [tasks: ['build'], label: 'Build', returnStdout: true]]]
        ToolCommand.builder().tasks(['deploy']).build()                                                 | 'delivery'        | MAVEN   || [delivery: [maven: [goals: ['deploy']]]]
    }

    def "the environment becomes the env map, a value keeping any further '=' it contains"() {
        when:
        def command = ToolCommand.builder().environment(['JAVA_OPTS=-Dfile.encoding=UTF-8 -Dx=y', 'EMPTY=',
                'NO_SEPARATOR', '=no-name', 'NAME = spaced ', 'CI=true', 'CI=false']).build()

        then:
        command.environmentMap() == [JAVA_OPTS: '-Dfile.encoding=UTF-8 -Dx=y', EMPTY: '', NAME: 'spaced', CI: 'false']
        command.environmentMap().keySet() as List == ['JAVA_OPTS', 'EMPTY', 'NAME', 'CI']
        NONE.environmentMap() == [:]
    }
}
