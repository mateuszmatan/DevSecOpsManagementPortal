package com.bbh.itss.dso.portal.domain.discovery

import spock.lang.Specification

class RepoStackDetectorSpec extends Specification {

    def "detects maven stack with java version and jacoco"() {
        given:
        def tree = [
                fileExists     : { String p -> p == 'pom.xml' },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p ->
                    Optional.of('''<project>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
        </dependency>
    </dependencies>
</project>''')
                }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == 'MAVEN'
        stack.primaryLanguage == 'JAVA'
        stack.javaVersion == '17'
        stack.unitTestTasks == 'test jacoco:report'
        stack.deployTarget == 'VM'
        stack.detectedMarkers.contains('pom.xml')
    }

    def "detects gradle stack with wrapper version, kotlin, and openshift dockerfile"() {
        given:
        def tree = [
                fileExists     : { String p -> p in ['build.gradle.kts', 'Dockerfile'] },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p ->
                    if (p == 'build.gradle.kts') {
                        return Optional.of('''plugins {
    kotlin("jvm") version "1.9.22"
    id("jacoco")
}
kotlin {
    jvmToolchain(21)
}''')
                    }
                    if (p == 'gradle/wrapper/gradle-wrapper.properties') {
                        return Optional.of('distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip')
                    }
                    Optional.empty()
                }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == 'GRADLE'
        stack.primaryLanguage == 'KOTLIN'
        stack.javaVersion == '21'
        stack.gradleVersion == '8.5'
        stack.unitTestTasks == 'test jacocoTestReport'
        stack.deployTarget == 'OPENSHIFT'
        stack.dockerDetected
        stack.detectedMarkers.contains('build.gradle.kts')
        stack.detectedMarkers.contains('Dockerfile')
    }

    def "detects stage 0 node build with cypress and angular"() {
        given:
        def tree = [
                fileExists     : { String p -> p in ['pom.xml', 'package.json'] },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p ->
                    if (p == 'pom.xml') {
                        return Optional.of('<project><properties><maven.compiler.source>17</maven.compiler.source></properties></project>')
                    }
                    if (p == 'package.json') {
                        return Optional.of('''{
  "name": "portal-gui",
  "engines": { "node": "^20.11.0" },
  "devDependencies": {
    "cypress": "^13.6.0"
  },
  "dependencies": {
    "@angular/core": "^18.0.0"
  }
}''')
                    }
                    Optional.empty()
                }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == 'MAVEN'
        stack.nodeBuildEnabled
        stack.nodeVersion == '^20.11.0'
        stack.testRunner == 'CYPRESS'
        stack.framework == 'ANGULAR'
        stack.detectedMarkers.contains('package.json')
    }

    def "detects liquibase stack"() {
        given:
        def tree = [
                fileExists     : { String p -> p == 'changelog.xml' },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p -> Optional.of('<databaseChangeLog/>') }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == 'LIQUIBASE'
        stack.liquibaseDetected
        stack.buildTasks == 'validate'
        stack.unitTestTasks == 'updateSQL validate'
    }

    def "detects flutter and dart stack"() {
        given:
        def tree = [
                fileExists     : { String p -> p == 'pubspec.yaml' },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p -> Optional.of('name: mobile_app') }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == 'FLUTTER'
        stack.primaryLanguage == 'DART'
        stack.framework == 'FLUTTER'
    }

    def "detects python pip stack"() {
        given:
        def tree = [
                fileExists     : { String p -> p == 'requirements.txt' },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p -> Optional.of('pytest==7.4.0\n') }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == 'PIP'
        stack.primaryLanguage == 'PYTHON'
        stack.testRunner == 'PYTEST'
        stack.unitTestTasks == 'pytest --junitxml=junit.xml'
    }

    def "detects sonar defaults when sonar-project.properties is missing"() {
        given:
        def tree = [
                fileExists     : { String p -> p == 'pom.xml' },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p ->
                    Optional.of('''<project>
    <artifactId>payment-service</artifactId>
    <modules>
        <module>core-service</module>
        <module>api-service</module>
    </modules>
</project>''')
                }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        !stack.sonarPropertiesDetected
        stack.sonarProjectKey == 'com.bbh:payment-service'
        stack.sonarModules == ['core-service', 'api-service']
        stack.sonarSources == 'src/main/java'
        stack.sonarTests == 'src/test/java'
        stack.summaryMessage.contains('com.bbh:payment-service')
    }

    def "detects existing sonar-project.properties"() {
        given:
        def tree = [
                fileExists     : { String p -> p in ['pom.xml', 'sonar-project.properties'] },
                directoryExists: { String p -> false },
                listFiles      : { String p -> [] },
                readFile       : { String p -> Optional.of('<project><artifactId>demo</artifactId></project>') }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.sonarPropertiesDetected
        stack.detectedMarkers.contains('sonar-project.properties')
    }

    def "detects a repository of SQL and PL/SQL files without a changelog as SQL, with its one top folder"() {
        given:
        def files = ['db/migrations/1_orders.sql', 'db/code/pkg_orders.pks', 'db/code/pkg_orders.pkb', 'README.md',
                     '.github/setup.sql']
        def tree = [
                fileExists     : { String p -> p in files },
                directoryExists: { String p -> p == 'db' },
                listFiles      : { String p -> files },
                readFile       : { String p -> Optional.empty() }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == 'SQL'
        stack.primaryLanguage == 'SQL'
        stack.sonarSources == 'db'
        stack.resolvedParameters.collect { [it.field(), it.value()] }.containsAll([
                ['buildTool', 'SQL'], ['database.searchPath', 'db'], ['buildToolAutoSetup', 'true']])
        stack.resolvedParameters.find { it.field() == 'buildTool' }.reason() ==
                'only SQL and PL/SQL files found, no build file and no Liquibase changelog'
    }

    def "SQL files next to #marker are not detected as SQL: #tool"() {
        given:
        def files = ['db/migrations/1_orders.sql', marker]
        def tree = [
                fileExists     : { String p -> p in files },
                directoryExists: { String p -> false },
                listFiles      : { String p -> files },
                readFile       : { String p -> p == marker ? Optional.of('{}') : Optional.empty() }
        ] as RepoFileTree

        expect:
        RepoStackDetector.detect(tree).buildTool == tool

        where:
        marker                 || tool
        'pom.xml'              || 'MAVEN'
        'build.gradle'         || 'GRADLE'
        'db/changelog.sql'     || 'LIQUIBASE'
        'changelog.xml'        || 'LIQUIBASE'
        'package.json'         || 'NPM'
        'requirements.txt'     || 'PIP'
    }

    def "the build tool is detected in the order of the library: #files give #tool"() {
        given:
        def tree = [
                fileExists     : { String p -> p in files },
                directoryExists: { String p -> false },
                listFiles      : { String p -> files },
                readFile       : { String p -> p in files ? Optional.of(p == 'package.json' ? '{"name": "web"}' : '') : Optional.empty() }
        ] as RepoFileTree

        when:
        def stack = RepoStackDetector.detect(tree)

        then:
        stack.buildTool == tool
        stack.resolvedParameters.find { it.field() == 'buildTool' }?.reason() == reason

        where:
        files                                                          || tool        | reason
        ['pubspec.yaml', 'pom.xml', 'package.json']                    || 'FLUTTER'   | 'pubspec.yaml found'
        ['Podfile', 'pom.xml']                                         || 'IOS'       | 'Podfile found (iOS)'
        ['Package.swift', 'package.json']                              || 'IOS'       | 'Package.swift found (iOS)'
        ['App.xcodeproj/project.pbxproj', 'build.gradle']              || 'IOS'       | 'App.xcodeproj found (iOS)'
        ['App.xcworkspace/contents.xcworkspacedata']                   || 'IOS'       | 'App.xcworkspace found (iOS)'
        ['pom.xml', 'build.gradle', 'package.json']                    || 'MAVEN'     | 'pom.xml found'
        ['settings.gradle', 'requirements.txt']                        || 'GRADLE'    | 'Gradle build script found'
        ['settings.gradle.kts']                                        || 'GRADLE'    | 'Gradle build script found'
        ['gradle/wrapper/gradle-wrapper.properties']                   || 'GRADLE'    | 'Gradle build script found'
        ['gradlew', 'package.json']                                    || 'NPM'       | 'package.json found (Node.js)'
        ['build.gradle', 'app/src/main/AndroidManifest.xml']           || 'ANDROID'   | 'Gradle build script and AndroidManifest.xml found (Android)'
        ['build.gradle.kts', 'AndroidManifest.xml']                    || 'ANDROID'   | 'Gradle build script and AndroidManifest.xml found (Android)'
        ['app/src/main/AndroidManifest.xml']                           || null        | null
        ['orders.sln', 'requirements.txt']                             || 'DOTNET'    | 'orders.sln found (C# / .NET)'
        ['db/changelog.xml', 'requirements.txt', 'package.json']       || 'LIQUIBASE' | 'Liquibase changelog found'
        ['requirements.txt', 'package.json']                           || 'PIP'       | 'Python project files found'
        ['setup.py', 'angular.json']                                   || 'PIP'       | 'Python project files found'
        ['Pipfile']                                                    || 'PIP'       | 'Python project files found'
        ['package.json', 'db/tables/orders.sql']                       || 'NPM'       | 'package.json found (Node.js)'
        ['angular.json', 'package.json']                               || 'NPM'       | 'package.json and angular.json found (Angular)'
        ['frontend/package.json', 'README.md']                         || null        | null
        ['README.md']                                                  || null        | null
    }

    def "a Python and an npm repository give the commands the library runs by default, and an npm one no build command"() {
        given:
        def treeOf = { List<String> files ->
            [fileExists     : { String p -> p in files },
             directoryExists: { String p -> false },
             listFiles      : { String p -> files },
             readFile       : { String p -> p in files ? Optional.of('{"name": "web", "devDependencies": {"@angular/core": "19"}}') : Optional.empty() }
            ] as RepoFileTree
        }

        when:
        def python = RepoStackDetector.detect(treeOf(['requirements.txt']))
        def npm = RepoStackDetector.detect(treeOf(['package.json']))

        then:
        python.buildTasks == 'pip install -r requirements.txt'
        python.unitTestTasks == 'pytest --junitxml=junit.xml'
        python.sonarSources == 'src'
        npm.buildTasks == null
        npm.unitTestTasks == 'npm test'
        npm.nodeBuildEnabled
        npm.framework == 'ANGULAR'
        npm.sonarSources == 'src'
    }
}
