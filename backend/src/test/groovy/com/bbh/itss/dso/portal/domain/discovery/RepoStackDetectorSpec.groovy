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
        stack.unitTestTasks == 'pytest'
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
}
