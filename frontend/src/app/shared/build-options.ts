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
};

/** How to tell the build tool of a service from its code. */
export const TOOL_HINTS: Record<BuildTool, string> = {
  GRADLE: 'The code has a build.gradle file',
  MAVEN: 'The code has a pom.xml file',
  FLUTTER: 'The code has a pubspec.yaml file',
  LIQUIBASE: 'The code has changelog files (changelog.xml/yaml/sql) or liquibase.properties',
};

export const TARGET_LABELS: Record<DeployTarget, string> = {
  VM: 'Virtual machine',
  OPENSHIFT: 'OpenShift',
};

export const GRADLE_MAVEN_FLUTTER = optionsOf(TOOL_LABELS);
export const VM_OPENSHIFT = optionsOf(TARGET_LABELS);

export const agentLabels = (value: string, required = true) =>
  text(
    value,
    ...(required ? [filled] : []),
    maxItems(commaItems, 20),
    eachItem(commaItems, /^.{1,100}$/, 'At most 100 characters per label'),
    fitsColumn(commaItems, ',', 1000),
  );
