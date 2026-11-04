import { PipelineType } from '../core/models';
import {
  allowedBy,
  deliveryNeeds,
  parameterProblem,
  parseOracleUrl,
  selectsTest,
  urlProblem,
} from './database-rules';

declare const process: {
  getBuiltinModule(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };
};

interface Cases {
  selectsTest: { filter: string; selects: boolean }[];
  urls: { url: string; hosts?: string[]; service?: string; problem?: 'SECRET' | 'FORM' }[];
  allowed: { entries: string[]; url: string; allowed: boolean }[];
}

/** The cases the backend specs check too, read from the frontend folder the unit tests run in. */
const CASES: Cases = JSON.parse(
  process
    .getBuiltinModule('node:fs')
    .readFileSync('../backend/src/test/resources/database-rules-cases.json', 'utf8'),
);

describe('database rules', () => {
  it.each(CASES.selectsTest)('selectsTest($filter) is $selects', ({ filter, selects }) => {
    expect(selectsTest(filter)).toBe(selects);
  });

  it('counts a blank or missing filter as deploying the test changesets', () => {
    expect([null, undefined, '  '].map(selectsTest)).toEqual([true, true, true]);
  });

  it.each(CASES.urls)('reads $url', ({ url, hosts, service, problem }) => {
    expect(urlProblem(url)).toBe(problem ?? null);
    expect(parseOracleUrl(url)).toEqual(problem ? null : { hosts, service });
  });

  it('finds no problem in an empty URL', () => {
    expect([null, undefined, ' '].map(urlProblem)).toEqual([null, null, null]);
    expect(parseOracleUrl('')).toBeNull();
  });

  it.each(CASES.allowed)('allows $url by $entries: $allowed', ({ entries, url, allowed }) => {
    expect(allowedBy(parseOracleUrl(url)!, entries)).toBe(allowed);
  });

  it.each<[PipelineType, string[]]>([
    [
      'FULL',
      [
        'groupId',
        'snapshotRepositoryId',
        'snapshotRepositoryUrl',
        'releaseRepositoryId',
        'releaseRepositoryUrl',
      ],
    ],
    [
      'EXTENDED',
      [
        'groupId',
        'snapshotRepositoryId',
        'snapshotRepositoryUrl',
        'releaseRepositoryId',
        'releaseRepositoryUrl',
      ],
    ],
    ['SECURITY', ['groupId', 'snapshotRepositoryId', 'snapshotRepositoryUrl']],
    ['SAST', []],
    ['NEXUS_IQ', []],
  ])('a delivering %s pipeline needs %j', (type, needs) => {
    expect(deliveryNeeds(type)).toEqual(needs);
  });

  it.each([
    [['app_role=orders_app_role', 'tablespace=USERS'], null],
    [['App=x'], 'Each line must be name=value'],
    [['role'], 'Each line must be name=value'],
    [Array.from({ length: 51 }, (_, i) => `p${i}=x`), 'Each line must be name=value'],
    [['db_password=x'], 'Holds a name that looks like a secret'],
    [['app_user=orders_app'], 'Holds a name that looks like a secret'],
    [['api_key=x'], 'Holds a name that looks like a secret'],
    [['dso_env=rd'], 'Holds a name that looks like a secret'],
    [['jenkins_url=x'], 'Holds a name that looks like a secret'],
    [['app_dso_role=x'], null],
    [['x=${env}'], 'Holds a value that is not printable ASCII'],
    [['x= v4lue'], 'Holds a value that is not printable ASCII'],
  ])('checks the parameter lines %j', (lines, problem) => {
    expect(parameterProblem(lines)?.slice(0, problem?.length) ?? null).toBe(problem);
  });
});
