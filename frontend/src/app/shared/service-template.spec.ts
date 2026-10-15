import { FormControl } from '@angular/forms';
import { serviceTemplate } from '../testing/fixtures';
import {
  JOB_PLACEHOLDERS,
  SERVICE_PLACEHOLDERS,
  buildDefaults,
  fillTemplate,
  knownPlaceholders,
} from './service-template';

describe('the service template', () => {
  it.each([
    ['DevSecOps/{CODE}/{service}-{type}', 'NEXUS_IQ', 'DevSecOps/CERT/backend-api-nexusiq'],
    ['{code}-{service}', undefined, 'cert-backend-api'],
    ['Teams/{CODE}/{type}', undefined, 'Teams/CERT/{type}'],
    ['{code}/{branch}/{}', 'FULL', 'cert/{branch}/{}'],
    [null, 'FULL', ''],
  ] as const)('fills %s', (pattern, type, filled) => {
    expect(fillTemplate(pattern, 'CERT', 'backend-api', type)).toBe(filled);
  });

  it('names the placeholders a field does not know once each', () => {
    const service = knownPlaceholders(SERVICE_PLACEHOLDERS);
    const job = knownPlaceholders(JOB_PLACEHOLDERS);

    expect(service(new FormControl('{code}-{service}'))).toBeNull();
    expect(service(new FormControl('{type}-{env}-{type}'))).toEqual({
      rule: 'Unknown placeholder {type}, {env}: use {CODE}, {code}, {service}',
    });
    expect(job(new FormControl('{CODE}/{type}'))).toBeNull();
    expect(job(new FormControl(null))).toBeNull();
  });

  it('gives the build of each build tool', () => {
    const template = serviceTemplate();

    expect(buildDefaults(template, 'GRADLE')).toEqual({
      tasks: 'clean build',
      artifact: 'build/libs/*.jar',
      scanPattern: '**/build/libs/*.jar',
    });
    expect(buildDefaults(template, 'MAVEN')).toEqual({
      tasks: 'clean verify',
      artifact: 'target/*.jar',
      scanPattern: '**/target/*.jar',
    });
    expect(buildDefaults(template, 'FLUTTER')).toEqual({
      tasks: '',
      artifact: '',
      scanPattern: '**/pubspec.lock',
    });
    expect(buildDefaults(null, 'MAVEN')).toEqual({ tasks: '', artifact: '', scanPattern: '' });
  });
});
