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
