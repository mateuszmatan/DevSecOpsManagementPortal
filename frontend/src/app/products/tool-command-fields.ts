import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BuildTool, CommandPath, commandParts, scripted } from '../core/models';
import { Field, Fields, area, check, line, mono } from '@common/shared/fields';
import { SHELL_SAFE_ERROR } from '@common/shared/form-controls';
import { ToolCommandForm } from './product-form-model';

const SCRIPTED_NAMES: Partial<Record<BuildTool, string>> = {
  NPM: 'npm',
  PIP: 'pip',
  IOS: 'ios',
};

const SCRIPTED_BUILD: Partial<Record<BuildTool, string>> = {
  PIP: 'pip install -r requirements.txt',
  IOS: 'xcodebuild -scheme MyApp -configuration Release build',
};

const SCRIPTED_TEST: Partial<Record<BuildTool, string>> = {
  NPM: 'npm test',
  PIP: 'pytest --junitxml=junit.xml',
  IOS: 'xcodebuild test -scheme MyApp -destination "platform=iOS Simulator,name=iPhone 15"',
};

/**
 * The fields of a command of npm, pip or iOS: one command line for the build and the unit tests, and the flags of the
 * SonarScanner. The parts the library does not read for these tools are left out.
 */
function scriptedFields(tool: BuildTool, path: string): Field[] {
  const name = SCRIPTED_NAMES[tool];
  const parts = path ? (commandParts(tool, path as CommandPath) ?? []) : ['tasks', 'directory', 'flags'];
  const tests = path === 'tests.unitTests';
  const fields: Field[] = [
    mono('tasks', tests ? 'Test command' : 'Build command', tests ? `${path}.commands` : `build.${name}.commands`, 6, {
      placeholder: (tests ? SCRIPTED_TEST : SCRIPTED_BUILD)[tool] ?? '',
      hint: `one command line; chain several with &&${tests ? `; left empty: ${SCRIPTED_TEST[tool] ?? 'no tests'}` : ''}`,
      help: tests
        ? 'The whole command line the Unit tests stage runs, with sh on Linux agents and bat on Windows agents. Write its options in the line itself.'
        : 'The whole command line the Build artifact stage runs in the source folder of the service, with sh on Linux agents and bat on Windows agents. Left empty, the stage builds nothing.',
    }),
    mono('directory', 'Directory', `${path}.dir`, 6, {
      placeholder: '.',
      hint: 'for a service in a subfolder: that folder',
      help: 'The folder of the workspace in which the command runs. Left empty: the workspace root.',
    }),
    area('flags', 'SonarScanner flags', `${path}.${name}.flags`, 6, {
      mono: true,
      placeholder: '-Dsonar.sources=src\n-Dsonar.exclusions=**/node_modules/**',
      hint: 'one per line',
      help: 'The library runs the SonarScanner for this build tool; these properties are added to its command line, such as -Dsonar.sources=src or -Dsonar.exclusions=... A property given here wins over the one the library adds.',
    }),
  ];
  return fields.filter((field) => parts.includes(field.key));
}

/** The fields of a Gradle or Maven command whose devsecops_config.yaml keys start with path. */
export function commandFields(
  tool: BuildTool,
  path: string,
  gradleExample = '',
  mavenExample = '',
  liquibaseExample = 'updateSQL',
): Field[] {
  if (scripted(tool)) {
    return scriptedFields(tool, path);
  }
  const maven = tool === 'MAVEN';
  const liquibase = tool === 'LIQUIBASE' || tool === 'SQL';
  const dotnet = tool === 'DOTNET';
  const toolName = dotnet ? 'dotnet' : liquibase ? 'liquibase' : maven ? 'maven' : 'gradle';
  const key = `${path}.${toolName}`;
  const taskKey = liquibase || dotnet ? 'commands' : maven ? 'goals' : 'tasks';
  const taskLabel = dotnet
    ? '.NET commands'
    : liquibase
      ? 'Liquibase commands'
      : maven
        ? 'Maven goals'
        : 'Gradle tasks';
  const taskPlaceholder = dotnet
    ? 'dotnet build MySolution.sln -c Release'
    : liquibase
      ? liquibaseExample
      : maven
        ? mavenExample
        : gradleExample;
  const taskHelp = dotnet
    ? 'Optional full commands that replace the automatic .NET step. Left empty, the library detects the mode from the repository (dotnet SDK CLI, classic MSBuild + NuGet, hybrid, Cake or NUKE) and runs restore, build, test with coverage or the SonarScanner for .NET begin/build/end.'
    : liquibase
    ? path === 'tools.sonar'
      ? 'SonarScanner runs automatically for SQL and PL/SQL files; set Flags under Advanced settings when overriding -Dsonar.sources, -Dsonar.inclusions, or -Dsonar.plsql.file.suffixes.'
      : path === 'tests.unitTests'
        ? 'The Liquibase command run during the Unit tests stage (for example validate or status). Line coverage and JUnit XML checks are skipped automatically when empty.'
        : path === 'asoc'
          ? 'The Liquibase command run before AppScan SAST packaging (for example updateSQL or validate) so rendered SQL and changelog scripts are scanned together with PL/SQL sources.'
          : 'The commands passed to liquibase, for example updateSQL, validate, or status. When left empty, the library runs updateSQL, with --changelog-file= and the changelog it finds at the repository root (else src/main/resources/changelog.xml) when Flags is empty too; for a service with database changes it runs validate when Flags is set, else nothing. A new service is prefilled with updateSQL.'
    : maven
      ? 'The goals the step passes to Maven: the mvn of the Maven home, otherwise ./mvnw when the repository has it, otherwise the mvn of the agent.'
      : 'The tasks the step passes to the Gradle wrapper (./gradlew) of the repository.';
  return [
    mono(
      'tasks',
      taskLabel,
      `${key}.${taskKey}`,
      6,
      {
        placeholder: taskPlaceholder,
        hint: 'separated by spaces',
        help: taskHelp,
      },
    ),
    mono('directory', 'Directory', `${key}.dir`, maven ? 3 : 6, {
      placeholder: '.',
      hint: 'for a service in a subfolder: that folder',
      help:
        liquibase && path === 'tools.sonar'
          ? 'The folder of the workspace in which sonar-scanner runs; SonarQube analyses only the files under it. For a service with database changes it must be "." or a folder that holds the package folder and, while the utPLSQL tests run, the folder of the test packages. Left empty: the workspace root.'
          : 'The folder of the workspace in which the command runs. Left empty: the workspace root.',
    }),
    ...(maven
      ? [
          mono('mavenHome', 'Maven home', `${key}.mvnPath`, 3, {
            placeholder: '/opt/maven',
            error: SHELL_SAFE_ERROR,
            help: 'The Maven installation on the agent. The step then runs its bin/mvn with M2_HOME set to this folder.',
          }),
        ]
      : []),
    area('flags', 'Flags', `${key}.flags`, 6, {
      mono: true,
      placeholder: liquibase
        ? path === 'tools.sonar'
          ? '-Dsonar.sources=db\n-Dsonar.plsql.file.suffixes=.sql,.pls,.pks,.pkb'
          : '--changelog-file=src/main/resources/changelog.xml'
        : maven
          ? '-B'
          : dotnet
            ? '-p:TreatWarningsAsErrors=false'
            : '--refresh-dependencies',
      hint: 'one per line',
      help:
        liquibase && path === 'tools.sonar'
          ? 'SonarScanner properties passed to sonar-scanner, such as -Dsonar.sources=db, -Dsonar.inclusions=**/*.sql,**/*.pkb, or -Dsonar.plsql.file.suffixes=.sql,.pls,.pks,.pkb.'
          : liquibase
            ? 'Liquibase options added before the command, such as --changelog-file=..., --defaults-file=..., or --url=offline:oracle.'
            : 'Command line options added after the goals or tasks, such as system properties or profiles.',
    }),
    area('environment', 'Environment variables', `${key}.env`, 6, {
      mono: true,
      placeholder: 'JAVA_OPTS=-Xmx1g',
      hint: 'one NAME=value per line',
      help: 'Environment variables set for this command only.',
    }),
    line('label', 'Step label', `${key}.label`, 6, {
      hint: 'the name Jenkins shows for the step',
      help: 'Left empty, the step is named after the tool and the command, for example Gradle: clean build.',
    }),
    check('returnStdout', 'Return the output to the pipeline', `${key}.returnStdout`, 6, {
      help: 'Captures the console output of the command instead of printing it to the build log; the pipeline does not use the captured output.',
    }),
  ];
}

@Component({
  selector: 'dso-tool-command-fields',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<dso-fields [group]="group()" [fields]="fields()" />`,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class ToolCommandFields {
  readonly group = input.required<ToolCommandForm>();
  readonly tool = input.required<BuildTool>();
  readonly path = input.required<string>();
  readonly gradleExample = input('');
  readonly mavenExample = input('');
  readonly liquibaseExample = input('updateSQL');
  readonly part = input<'all' | 'tasks' | 'options' | 'flags'>('all');

  protected fields(): Field[] {
    const part = this.part();
    const fields = commandFields(
      this.tool(),
      this.path(),
      this.gradleExample(),
      this.mavenExample(),
      this.liquibaseExample(),
    );
    if (part === 'flags') {
      return fields.filter((field) => field.key === 'flags' || field.key === 'environment');
    }
    return part === 'all'
      ? fields
      : fields.filter((field) => (field.key === 'tasks') === (part === 'tasks'));
  }
}
