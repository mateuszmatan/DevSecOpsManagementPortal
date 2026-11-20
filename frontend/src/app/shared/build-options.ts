import { optionsOf } from '@common/shared/fields';
import {
  commaItems,
  eachItem,
  filled,
  fitsColumn,
  maxItems,
  text,
} from '@common/shared/form-controls';
import { BuildTool, DeployTarget } from '../core/models';

export const TOOL_LABELS: Record<BuildTool, string> = {
  GRADLE: 'Gradle',
  MAVEN: 'Maven',
  FLUTTER: 'Flutter',
  LIQUIBASE: 'Liquibase (SQL / PL/SQL)',
  DOTNET: 'C# / .NET (dotnet or MSBuild)',
  SQL: 'SQL / PL/SQL files (no changelog)',
  NPM: 'npm (Angular, Node.js)',
  PIP: 'pip (Python)',
  ANDROID: 'Android (Gradle)',
  IOS: 'iOS (Xcode)',
};

/** How to tell the build tool of a service from its code. */
export const TOOL_HINTS: Record<BuildTool, string> = {
  GRADLE: 'The code has a build.gradle or settings.gradle file and no AndroidManifest.xml',
  MAVEN: 'The code has a pom.xml file',
  FLUTTER: 'The code has a pubspec.yaml file',
  LIQUIBASE: 'The code has changelog files (changelog.xml/yaml/sql) or liquibase.properties',
  DOTNET: 'The code has a .sln, .csproj, .fsproj or .vbproj file, global.json or packages.config',
  SQL: 'The code has only SQL and PL/SQL files (.sql, .pks, .pkb, ...) and no Liquibase changelog; the library writes the changelog',
  NPM: 'The code has a package.json or angular.json file at its root and no Gradle, Maven, .NET or Python build',
  PIP: 'The code has a requirements.txt, pyproject.toml, setup.py or Pipfile file',
  ANDROID: 'The code has Gradle files and an AndroidManifest.xml (at the root or in app/src/main)',
  IOS: 'The code has a Podfile, Package.swift, .xcodeproj or .xcworkspace',
};

export const TARGET_LABELS: Record<DeployTarget, string> = {
  VM: 'Virtual machine',
  OPENSHIFT: 'OpenShift',
};

export const BUILD_TOOLS = optionsOf(TOOL_LABELS);
/** The build tools a new service may start with: a database service needs its own changelog or databases. */
export const DEFAULT_BUILD_TOOLS = BUILD_TOOLS.filter(
  ({ value }) => value !== 'LIQUIBASE' && value !== 'SQL',
);
export const VM_OPENSHIFT = optionsOf(TARGET_LABELS);

export const agentLabels = (value: string, required = true) =>
  text(
    value,
    ...(required ? [filled] : []),
    maxItems(commaItems, 20),
    eachItem(commaItems, /^.{1,100}$/, 'At most 100 characters per label'),
    fitsColumn(commaItems, ',', 1000),
  );
