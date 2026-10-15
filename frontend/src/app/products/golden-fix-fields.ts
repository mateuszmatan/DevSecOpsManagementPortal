import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { GoldenFixPolicy } from '../core/models';
import { Field, Fields, area, check, choice, count, line, mono, tristate } from '@common/shared/fields';
import {
  GOLDEN_FIX_ECOSYSTEMS,
  GlobalGoldenFixForm,
  ServiceGoldenFixForm,
} from './product-form-model';

const command = (key: string, tool: string, span = 6): Field =>
  mono(`verify${key}Command`, `${tool} command`, `verify.commands.${key.toLowerCase()}`, span, {
    placeholder: 'Library default',
    hint: `What builds the fix in ${tool} projects`,
  });

const COMMANDS: Field[] = [
  command('Maven', 'Maven'),
  command('Gradle', 'Gradle'),
  command('Npm', 'npm', 4),
  command('Pip', 'pip', 4),
  command('Pub', 'pub', 4),
];

@Component({
  selector: 'dso-golden-fix-fields',
  imports: [Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @let g = group();
    <dso-fields [group]="g" [fields]="policyFields()" />
    <h5 class="sub-heading span-12">Verification build</h5>
    <dso-fields [group]="g" [fields]="verifyFields()" />
    <dso-fields [group]="g" [fields]="commands" />
    <h5 class="sub-heading span-12">Commits</h5>
    <dso-fields [group]="g" [fields]="commitFields()" />
  `,
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class GoldenFixFields {
  readonly group = input.required<GlobalGoldenFixForm | ServiceGoldenFixForm>();
  readonly inherited = input<GoldenFixPolicy | null>(null);
  readonly complete = input(false);

  protected readonly commands = COMMANDS;

  protected policyFields(): Field[] {
    const g = this.inherited();
    return [
      choice(
        'ecosystems',
        'Ecosystems',
        GOLDEN_FIX_ECOSYSTEMS.map((value) => ({ value, label: value })),
        'goldenFix.ecosystems',
        5,
        {
          multiple: true,
          hint: this.explain('The kinds of dependencies GoldenFix upgrades', g?.ecosystems),
        },
      ),
      count('minThreatLevel', 'Minimum threat level', 'minThreatLevel', 3, {
        min: 0,
        max: 10,
        hint: this.explain(
          'Only vulnerabilities Nexus IQ rates at least this high, from 0 to 10',
          g?.minThreatLevel,
        ),
      }),
      this.complete()
        ? check('onlyDirectDependencies', 'Direct dependencies only', 'onlyDirectDependencies', 4)
        : choice(
            'onlyDirectDependencies',
            'Dependencies',
            tristate('Global value', 'Direct dependencies only', 'Direct and transitive'),
            'onlyDirectDependencies',
            4,
            {
              hint: this.explain(
                'Direct ones are named by the service itself; transitive ones come with them',
                g?.onlyDirectDependencies,
              ),
            },
          ),
      area('goldenVersionTypes', 'Golden version types', 'goldenVersionTypes', 6, {
        mono: true,
        placeholder: 'recommended-non-breaking',
        hint: this.explain(
          'The kinds of safe version Nexus IQ suggests, one per line, preferred first',
          g?.goldenVersionTypes,
        ),
      }),
      area('excludeDirs', 'Excluded folders', 'excludeDirs', 6, {
        mono: true,
        hint: this.explain('Folders GoldenFix leaves alone, one per line', g?.excludeDirs),
      }),
    ];
  }

  protected verifyFields(): Field[] {
    const g = this.inherited();
    return [
      this.complete()
        ? check(
            'verifyEnabled',
            'Build the fix before opening the pull request',
            'verify.enabled',
            4,
          )
        : choice(
            'verifyEnabled',
            'Verification',
            tristate('Global value', 'Build the fix first', 'Do not build it'),
            'verify.enabled',
            4,
            {
              hint: this.explain(
                'A build of the fixed service catches upgrades that break it',
                g?.verifyEnabled,
              ),
            },
          ),
      count('verifyMaxAttempts', 'Attempts', 'verify.maxAttempts', 4, {
        min: 1,
        hint: this.explain(
          'How many times GoldenFix may run the verification build',
          g?.verifyMaxAttempts,
        ),
      }),
      count('verifyTimeoutMinutes', 'Timeout (minutes)', 'verify.timeoutMinutes', 4, {
        min: 1,
        hint: this.explain('How long one verification build may take', g?.verifyTimeoutMinutes),
      }),
    ];
  }

  protected commitFields(): Field[] {
    const g = this.inherited();
    return [
      line('commitAuthorName', 'Author name', 'commitAuthorName', 4, {
        hint: this.explain('The name the pull request commits are made under', g?.commitAuthorName),
      }),
      line('commitAuthorEmail', 'Author e-mail', 'commitAuthorEmail', 5, {
        hint: this.explain('The e-mail address of that author', g?.commitAuthorEmail),
      }),
      mono('timeZone', 'Time zone', 'timeZone', 3, {
        placeholder: 'Europe/Warsaw',
        hint: this.explain('The time zone of the commit times', g?.timeZone),
        error: 'Must be a time zone ID such as Europe/Warsaw or UTC',
      }),
    ];
  }

  private explain(
    hint: string,
    value: string | number | boolean | readonly string[] | null | undefined,
  ): string {
    if (this.complete()) {
      return hint;
    }
    const text = Array.isArray(value)
      ? value.join(', ')
      : typeof value === 'boolean'
        ? value
          ? 'yes'
          : 'no'
        : (value ?? '');
    return `${hint} · left empty: ${text === '' ? 'global value' : text}`;
  }
}
