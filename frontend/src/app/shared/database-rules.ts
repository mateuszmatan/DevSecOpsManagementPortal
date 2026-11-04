import { ValidatorFn } from '@angular/forms';
import { optional } from '@common/shared/form-controls';
import { DatabaseSettings, PipelineType } from '../core/models';

/**
 * The value formats and rules of the database changes of a Liquibase service and of their Library defaults, as the
 * DevSecOps library and the portal backend check them (DatabaseFormats, OracleUrl and ContextFilter).
 */
export const PATH = /^(?!(.*\/)?\.\.(\/|$))[A-Za-z0-9._-]+(\/[A-Za-z0-9._-]+)*$/;
export const SEARCH_PATH = /^\.$|^(?!(.*\/)?\.\.(\/|$))[A-Za-z0-9._-]+(\/[A-Za-z0-9._-]+)*$/;
export const CHANGELOG_SUFFIX = /^.*\.(xml|yaml|yml|json|sql)$/;
export const SCHEMA = /^[A-Za-z][A-Za-z0-9_$#]{0,127}$/;
export const CREDENTIALS = /^[A-Za-z0-9._-]{1,200}$/;
export const CONTEXTS = /^[A-Za-z0-9_!@(), -]{0,200}$/;
export const MAVEN = /^[A-Za-z0-9._-]{1,200}$/;
export const ABSOLUTE = /^\/[^'"\x00-\x1f\x7f]{0,499}$/;
export const REPOSITORY_URL = /^https:\/\/[A-Za-z0-9.-]+(:[0-9]+)?(\/[A-Za-z0-9._~/-]*)?$/;
export const TOOL_URL =
  /^https:\/\/[A-Za-z0-9.-]+(:[0-9]+)?\/[A-Za-z0-9._~/-]*\.(tar\.gz|tgz|zip|jar)$/;
export const ARCHIVE = /^.*\.(tar\.gz|tgz|zip)$/;
export const JAR = /^.*\.jar$/;
export const ZIP = /^.*\.zip$/;
export const SHA256 = /^[0-9a-f]{64}$/;
export const PARAMETER_NAME = /^[a-z][a-z0-9_.]{0,63}$/;
export const SECRET_NAME =
  /^(liquibase_|dso_|jenkins)|password|passwd|pwd|secret|token|credential|key|user/i;
export const PARAMETER_VALUE = /^([\x21-\x7e]([\x20-\x7e]{0,498}[\x21-\x7e])?)?$/;
export const TARGET = /^[A-Za-z0-9*][A-Za-z0-9.*-]{0,252}(\/[A-Za-z0-9_$#.*-]{1,128})?$/;
export const ORACLE_VERSION = /^[0-9]{1,2}(\.[0-9]{1,2})?$/;
export const UTPLSQL_PATHS = /^[A-Za-z0-9_$#.:,]{0,400}$/;

export const CHANGELOG_PATH_MESSAGE =
  'Must be a relative path of letters, digits, ".", "_" and "-", separated by "/", without ".." and not starting with "/"';
export const CHANGELOG_SUFFIX_MESSAGE = 'Must end with .xml, .yaml, .yml, .json or .sql';
export const SEARCH_PATH_MESSAGE =
  'Must be "." or a relative path inside the repository, without ".."';
export const SEARCH_ROOT_PATH_MESSAGE =
  'Must be a relative path inside the search root, without ".."';
export const SCHEMA_MESSAGE =
  'Must be an Oracle schema name: a letter, then letters, digits, "_", "$" or "#", at most 128';
export const IGNORE_OBJECT_MESSAGE = 'Must be an Oracle object name';
export const CREDENTIALS_MESSAGE =
  'Must be a Jenkins credentials ID of letters, digits, ".", "_" and "-" (at most 200)';
export const CONTEXTS_MESSAGE =
  'May hold only letters, digits, blanks, "_", "!", "@", "(", ")", "," and "-" (at most 200)';
export const MAVEN_MESSAGE = 'May hold only letters, digits, ".", "_" and "-" (at most 200)';
export const ABSOLUTE_MESSAGE = 'Must be an absolute path';
export const MAVEN_HOME_MESSAGE = 'Must be an absolute path of a Maven installation';
export const REPOSITORY_URL_MESSAGE =
  'Must be an https URL such as https://nexus.bbh.com/repository/db-snapshots, without a user or a password';
export const TOOL_URL_MESSAGE =
  'Must be an https URL of a .tar.gz, .tgz, .zip or .jar file, without a user or a password';
export const ARCHIVE_MESSAGE = 'Must end with .tar.gz, .tgz or .zip';
export const JAR_MESSAGE = 'Must end with .jar';
export const ZIP_MESSAGE = 'Must end with .zip';
export const SHA256_MESSAGE = 'Must be the 64 lower-case hexadecimal digits of the file';
export const TARGET_MESSAGE =
  'Must be "host" or "host/SERVICE" of letters, digits, ".", "-" and "*"';
export const ORACLE_VERSION_MESSAGE = 'Must be an Oracle version such as 19.0 or 23.0';
export const UTPLSQL_PATHS_MESSAGE =
  'May hold only letters, digits, "_", "$", "#", ".", ":" and "," (at most 400)';

/** Refuses a value that is set and does not match, with the message of the format. */
export const matching =
  (pattern: RegExp, message: string): ValidatorFn =>
  (control) => {
    const value = optional(String(control.value ?? ''));
    return value === null || pattern.test(value) ? null : { rule: message };
  };

const PARAMETER_FORM =
  'Each line must be name=value, where the name starts with a lower-case letter and holds only lower-case letters, digits, "_" and "." (at most 64), and at most 50 lines';
const PARAMETER_SECRET =
  'Holds a name that looks like a secret or a variable of the agent, which the library refuses in the changelog: changelog parameters are written into the package; keep secrets in Jenkins credentials';
const PARAMETER_VALUE_MESSAGE =
  'Holds a value that is not printable ASCII of at most 500 characters, or that starts or ends with a blank, or holds "${"';

/** The first problem of changelog parameter lines, name=value each, or null. */
export function parameterProblem(lines: readonly string[]): string | null {
  const pairs = lines.map((line) => {
    const separator = line.indexOf('=');
    return separator < 0 ? [line] : [line.slice(0, separator), line.slice(separator + 1)];
  });
  if (
    lines.length > 50 ||
    pairs.some(([name, value]) => value === undefined || !PARAMETER_NAME.test(name))
  ) {
    return PARAMETER_FORM;
  }
  if (pairs.some(([name]) => SECRET_NAME.test(name))) {
    return PARAMETER_SECRET;
  }
  return pairs.some(([, value]) => !PARAMETER_VALUE.test(value) || value.includes('${'))
    ? PARAMETER_VALUE_MESSAGE
    : null;
}

export type UrlProblem = 'SECRET' | 'FORM';

export interface OracleUrl {
  hosts: string[];
  service: string;
}

const THIN = 'jdbc:oracle:thin:';
const PREFIX = `${THIN}@`;
const HOST = '[A-Za-z0-9][A-Za-z0-9.-]{0,252}';
const EZCONNECT = new RegExp(
  `^(?://|tcps?://)?(${HOST}(?::[0-9]{1,5})?(?:,${HOST}(?::[0-9]{1,5})?)*)/([A-Za-z0-9_$#.-]{1,128})(?:\\?(.*))?$`,
);
const HOST_PORT = new RegExp(`^(${HOST})(?::([0-9]{1,5}))?$`);
const SID = new RegExp(`^(${HOST}):([0-9]{1,5}):([A-Za-z0-9_$#]{1,30})$`);
const DESCRIPTOR = /^\(DESCRIPTION=[A-Za-z0-9=()._,:\t\n\x0B\f\r -]+\)$/i;
const URL_PARAMETER_VALUE = /^"?[A-Za-z0-9._=, -]{1,200}"?$/;
const URL_PARAMETERS = [
  'ssl_server_dn_match',
  'ssl_server_cert_dn',
  'connect_timeout',
  'transport_connect_timeout',
  'retry_count',
  'retry_delay',
];
const SECRETS = ['user=', 'username=', 'password=', 'pwd='];

/** Why a JDBC URL is refused: it holds a user or a password, or it is no Oracle thin URL the library deploys to. */
export function urlProblem(url: string | null | undefined): UrlProblem | null {
  const value = optional(url);
  return value === null || parseOracleUrl(value) ? null : holdsSecret(value) ? 'SECRET' : 'FORM';
}

/** The hosts and the service of an Oracle thin URL in lower case, or null for a URL that is refused. */
export function parseOracleUrl(url: string | null | undefined): OracleUrl | null {
  const value = optional(url);
  if (value === null || !value.startsWith(PREFIX) || holdsSecret(value)) {
    return null;
  }
  const connect = value.slice(PREFIX.length);
  const parsed = ezConnect(connect) ?? sid(connect) ?? descriptor(connect);
  return (
    parsed && {
      hosts: parsed.hosts.map((host) => host.trim().toLowerCase()),
      service: (parsed.service ?? '').trim().toLowerCase(),
    }
  );
}

/** Whether every host matches an entry, "host" or "host/SERVICE" with "*" for any run of characters. */
export function allowedBy(url: OracleUrl, entries: readonly string[]): boolean {
  return url.hosts.every((host) =>
    entries.some((entry) => {
      const text = entry.trim().toLowerCase();
      const slash = text.indexOf('/');
      const service = slash < 0 ? null : text.slice(slash + 1);
      return (
        glob(slash < 0 ? text : text.slice(0, slash), '[a-z0-9.-]*').test(host) &&
        (service === null || glob(service, '[a-z0-9_$#.-]*').test(url.service))
      );
    }),
  );
}

function holdsSecret(url: string): boolean {
  if (!url.startsWith(PREFIX)) {
    return url.startsWith(THIN) && url.indexOf('@') > THIN.length;
  }
  const lower = url.toLowerCase().replace(/\s+/g, '');
  return url.indexOf('@', PREFIX.length) >= 0 || SECRETS.some((secret) => lower.includes(secret));
}

type Parsed = { hosts: string[]; service: string | null } | null;

function ezConnect(connect: string): Parsed {
  const url = EZCONNECT.exec(connect);
  if (!url || (url[3] !== undefined && !urlParameters(url[3]))) {
    return null;
  }
  const hosts = url[1].split(',').map((host) => HOST_PORT.exec(host));
  return hosts.every((host) => host && port(host[2]))
    ? { hosts: hosts.map((host) => host![1]), service: url[2] }
    : null;
}

function sid(connect: string): Parsed {
  const url = SID.exec(connect);
  return url && port(url[2]) ? { hosts: [url[1]], service: url[3] } : null;
}

function descriptor(connect: string): Parsed {
  if (!DESCRIPTOR.test(connect) || !balanced(connect)) {
    return null;
  }
  const hosts = values(connect, 'HOST');
  const service = values(connect, 'SERVICE_NAME');
  return hosts.length
    ? { hosts, service: (service.length ? service : values(connect, 'SID'))[0] ?? null }
    : null;
}

function urlParameters(query: string): boolean {
  return query.split('&').every((parameter) => {
    const separator = parameter.indexOf('=');
    return (
      separator > 0 &&
      URL_PARAMETERS.includes(parameter.slice(0, separator).toLowerCase()) &&
      URL_PARAMETER_VALUE.test(parameter.slice(separator + 1))
    );
  });
}

const port = (value: string | undefined) =>
  value === undefined || (Number(value) >= 1 && Number(value) <= 65535);

function balanced(text: string): boolean {
  let depth = 0;
  for (const character of text) {
    depth += character === '(' ? 1 : character === ')' ? -1 : 0;
    if (depth < 0) {
      return false;
    }
  }
  return depth === 0;
}

const values = (descriptor: string, key: string) =>
  [...descriptor.replace(/\s+/g, '').matchAll(new RegExp(`\\(${key}=([^()]*)\\)`, 'gi'))].map(
    (match) => match[1],
  );

const glob = (entry: string, any: string) =>
  new RegExp(
    `^${entry
      .split('*')
      .map((part) => part.replace(/[.*+?^${}()|[\]\\/-]/g, '\\$&'))
      .join(any)}$`,
  );

const TRUE = ':TRUE';
const FALSE = ':FALSE';

/**
 * Whether a Liquibase run with this context filter deploys the changesets marked context:test, by the algorithm the
 * DevSecOps library specifies: a filter that cannot be parsed counts as selecting them.
 */
export function selectsTest(filter: string | null | undefined): boolean {
  let text = filter ?? '';
  if (!text.trim()) {
    return true;
  }
  for (let close = text.indexOf(')'); close >= 0; close = text.indexOf(')')) {
    const open = text.lastIndexOf('(', close);
    if (open < 0 || close === open + 1) {
      return true;
    }
    const inner = matches(text.slice(open + 1, close)) ? TRUE : FALSE;
    text = `${text.slice(0, open)} ${inner} ${text.slice(close + 1)}`;
  }
  return text.includes('(') || matches(text);
}

function matches(expression: string): boolean {
  return split(expression.replace(/\s*,\s*/g, ' or '), /\s+or\s+/).some((part) =>
    split(part, /\s+and\s+/).every(item),
  );
}

/** Splits as Java does: a text without the separator stays whole, otherwise the empty parts at the end go. */
function split(text: string, separator: RegExp): string[] {
  const parts = text.split(separator);
  if (parts.length === 1) {
    return parts;
  }
  while (parts.length && parts[parts.length - 1] === '') {
    parts.pop();
  }
  return parts;
}

function item(text: string): boolean {
  let item = text.trim();
  if (/^@\s*(!|not )./.test(item)) {
    item = item.slice(1).trim();
  }
  const negated = item.startsWith('!') || item.toLowerCase().startsWith('not ');
  if (negated) {
    item = item.slice(item.startsWith('!') ? 1 : 4).trim();
  }
  if (item.startsWith('@')) {
    item = item.slice(1).trim();
  }
  const value =
    item === 'nocontexts'
      ? false
      : item === TRUE
        ? true
        : item === FALSE
          ? false
          : item.toLowerCase() === 'test';
  return negated !== value;
}

export type DeliveryKey = keyof Pick<
  DatabaseSettings,
  | 'groupId'
  | 'snapshotRepositoryId'
  | 'snapshotRepositoryUrl'
  | 'releaseRepositoryId'
  | 'releaseRepositoryUrl'
>;

export type RepositoryKey = Exclude<DeliveryKey, 'groupId'>;

const SNAPSHOT: readonly DeliveryKey[] = [
  'groupId',
  'snapshotRepositoryId',
  'snapshotRepositoryUrl',
];
const RELEASE: readonly DeliveryKey[] = [
  ...SNAPSHOT,
  'releaseRepositoryId',
  'releaseRepositoryUrl',
];

/** What a pipeline of the type needs while the package is delivered to Nexus. */
export function deliveryNeeds(type: PipelineType): readonly DeliveryKey[] {
  switch (type) {
    case 'FULL':
    case 'EXTENDED':
      return RELEASE;
    case 'SECURITY':
      return SNAPSHOT;
    default:
      return [];
  }
}
