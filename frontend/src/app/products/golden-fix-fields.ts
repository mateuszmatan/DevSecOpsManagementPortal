import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { GoldenFixPolicy } from '../core/models';
import {
  Field,
  Fields,
  area,
  check,
  choice,
  count,
  line,
  mono,
  tristate,
} from '@common/shared/fields';
import {
  GOLDEN_FIX_ECOSYSTEMS,
  GlobalGoldenFixForm,
  ServiceGoldenFixForm,
} from './product-form-model';

const command = (key: string, tool: string, manifest: string, fallback: string, span = 6): Field =>
  mono(`verify${key}Command`, `${tool} command`, `verify.commands.${key.toLowerCase()}`, span, {
    placeholder: 'Library default',
    hint: `What builds the fix in ${tool} projects`,
    help: `Runs with bash in the folder of each changed ${manifest}; exit code 0 passes the check. When no command is set, the library runs ${fallback}.`,
  });

const COMMANDS: Field[] = [
  command('Maven', 'Maven', 'pom.xml', 'mvn -B -q -DskipTests compile'),
  command(
    'Gradle',
    'Gradle',
    'Gradle build, properties or version catalog file',
    './gradlew --no-daemon --console=plain classes, or gradle without a wrapper',
  ),
  command(
    'Npm',
    'npm',
    'package.json',
    'npm ci --ignore-scripts, or npm install --ignore-scripts without a lock file',
    4,
  ),
  command(
    'Pip',
    'pip',
    'requirements, constraints or pyproject.toml file',
    'python3 -m pip install --dry-run of that file or of the project',
    4,
  ),
  command('Pub', 'pub', 'pubspec.yaml', 'flutter pub get, or dart pub get', 4),
];

const DIRECT_ONLY =
  'With direct dependencies only, GoldenFix leaves out the components Nexus IQ reports as transitive.';

const VERIFY =
  'When the build fails, GoldenFix lowers the proposed versions and builds again; when no set of versions builds, it opens no pull request.';

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
          help: 'Findings in other ecosystems are left for manual fixes. pypi stands for Python packages and pub for Dart and Flutter packages.',
        },
      ),
      count('minThreatLevel', 'Minimum threat level', 'minThreatLevel', 3, {
        min: 0,
        max: 10,
        hint: this.explain(
          'Only components with a policy violation that Nexus IQ rates at least this high, from 0 to 10',
          g?.minThreatLevel,
        ),
        help: 'Waived violations do not count. 2 and above is medium, 4 and above high, 8 and above critical.',
      }),
      this.complete()
        ? check('onlyDirectDependencies', 'Direct dependencies only', 'onlyDirectDependencies', 4, {
            help: DIRECT_ONLY,
          })
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
              help: DIRECT_ONLY,
            },
          ),
      area('goldenVersionTypes', 'Golden version types', 'goldenVersionTypes', 6, {
        mono: true,
        placeholder: 'recommended-non-breaking',
        hint: this.explain(
          'The Nexus IQ remediation types that count as a Golden Version, one per line',
          g?.goldenVersionTypes,
        ),
        help: 'They do not change the version GoldenFix picks; a version of one of these types is labelled Nexus IQ Golden Version in the pull request.',
      }),
      area('excludeDirs', 'Excluded folders', 'excludeDirs', 6, {
        mono: true,
        hint: this.explain('Folders GoldenFix leaves alone, one per line', g?.excludeDirs),
        help: 'Folder names, matched at any depth, whose manifests GoldenFix does not change. A list replaces the library list of .git, node_modules, target, build and similar folders.',
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
            { help: VERIFY },
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
              help: VERIFY,
            },
          ),
      count('verifyMaxAttempts', 'Attempts', 'verify.maxAttempts', 4, {
        min: 1,
        hint: this.explain(
          'How many times GoldenFix may run the verification build',
          g?.verifyMaxAttempts,
        ),
        help: 'After each failed build, every proposed version is lowered to the next lower one Nexus IQ offers; a dependency without one is dropped.',
      }),
      count('verifyTimeoutMinutes', 'Timeout (minutes)', 'verify.timeoutMinutes', 4, {
        min: 1,
        hint: this.explain('How long one verification build may take', g?.verifyTimeoutMinutes),
        help: 'The limit applies to each build command separately, one for each changed manifest folder.',
      }),
    ];
  }

  protected commitFields(): Field[] {
    const g = this.inherited();
    return [
      line('commitAuthorName', 'Author name', 'commitAuthorName', 4, {
        hint: this.explain('The name the pull request commits are made under', g?.commitAuthorName),
        help: 'Git records it as the author and the committer of the GoldenFix commit.',
      }),
      line('commitAuthorEmail', 'Author e-mail', 'commitAuthorEmail', 5, {
        hint: this.explain('The e-mail address of that author', g?.commitAuthorEmail),
        help: 'It does not have to receive mail; when none is set, the library uses a no-reply address.',
      }),
      mono('timeZone', 'Time zone', 'timeZone', 3, {
        placeholder: 'Europe/Warsaw',
        hint: this.explain('The time zone of the time stamp in the pull request name', g?.timeZone),
        help: 'The branch and the pull request are named GoldenFix-YYYYMMDDHHMM. Without a time zone, that of the Jenkins agent applies.',
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
    return `${hint} · left empty: ${text === '' ? 'the Library default' : text}`;
  }
}
