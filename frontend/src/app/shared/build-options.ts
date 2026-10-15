import { optionsOf } from '@common/shared/fields';
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
