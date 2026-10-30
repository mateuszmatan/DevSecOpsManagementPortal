import { configFileOf } from './config-file';

describe('configFileOf', () => {
  const files = (...names: string[]) => names.map((name) => new File([''], name));
  const names = (choice: ReturnType<typeof configFileOf>) =>
    choice && [choice.file.name, choice.skipped.map((file) => file.name)];

  it('reads the only chosen file, whatever its name', () => {
    expect(names(configFileOf(files('cert-gui-full.yaml')))).toEqual(['cert-gui-full.yaml', []]);
  });

  it('reads devsecops_config.yaml first among several files, whatever their order', () => {
    expect(
      names(configFileOf(files('config.yaml', 'Jenkinsfile', 'devsecops_config.yaml'))),
    ).toEqual(['devsecops_config.yaml', ['config.yaml', 'Jenkinsfile']]);
  });

  it('reads config.yaml among several files without devsecops_config.yaml', () => {
    expect(names(configFileOf(files('README.md', 'config.yaml')))).toEqual([
      'config.yaml',
      ['README.md'],
    ]);
  });

  it('matches the exact names only', () => {
    expect(
      configFileOf(
        files('pipeline-config.yaml', 'buildconfig.yaml', 'Config.yaml', 'devsecops_config.yml'),
      ),
    ).toBeUndefined();
  });

  it('chooses nothing from no file', () => {
    expect(configFileOf([])).toBeUndefined();
  });
});
