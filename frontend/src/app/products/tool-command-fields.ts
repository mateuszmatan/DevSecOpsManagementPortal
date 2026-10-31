import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BuildTool } from '../core/models';
import { Field, Fields, area, check, line, mono } from '@common/shared/fields';
import { SHELL_SAFE_ERROR } from '@common/shared/form-controls';
import { ToolCommandForm } from './product-form-model';

/** The fields of a Gradle or Maven command whose devsecops_config.yaml keys start with path. */
export function commandFields(
  tool: BuildTool,
  path: string,
  gradleExample = '',
  mavenExample = '',
  liquibaseExample = 'updateSQL',
): Field[] {
  const maven = tool === 'MAVEN';
  const liquibase = tool === 'LIQUIBASE';
  const toolName = liquibase ? 'liquibase' : maven ? 'maven' : 'gradle';
  const key = `${path}.${toolName}`;
  const taskKey = liquibase ? 'commands' : maven ? 'goals' : 'tasks';
  const taskLabel = liquibase ? 'Liquibase commands' : maven ? 'Maven goals' : 'Gradle tasks';
  const taskPlaceholder = liquibase ? liquibaseExample : maven ? mavenExample : gradleExample;
  const taskHelp = liquibase
    ? 'The commands passed to liquibase, for example updateSQL, validate, or status.'
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
      help: 'The folder of the workspace in which the command runs. Left empty: the workspace root.',
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
        ? '--changelog-file=src/main/resources/changelog.xml'
        : maven
          ? '-B'
          : '--refresh-dependencies',
      hint: 'one per line',
      help: 'Command line options added after the goals or tasks, such as system properties or profiles.',
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
  readonly part = input<'all' | 'tasks' | 'options'>('all');

  protected fields(): Field[] {
    const part = this.part();
    const fields = commandFields(
      this.tool(),
      this.path(),
      this.gradleExample(),
      this.mavenExample(),
    );
    return part === 'all'
      ? fields
      : fields.filter((field) => (field.key === 'tasks') === (part === 'tasks'));
  }
}
