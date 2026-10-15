import { BitbucketRepository, bitbucketRepositoryUrl } from './bitbucket';

function scm(overrides: Partial<BitbucketRepository> = {}): BitbucketRepository {
  return {
    repositoryUrl: null,
    type: null,
    apiUrl: null,
    workspace: null,
    projectKey: null,
    repoSlug: null,
    ...overrides,
  };
}

describe('bitbucketRepositoryUrl', () => {
  it('links the repository URL when the service sets one', () => {
    expect(
      bitbucketRepositoryUrl(
        scm({
          repositoryUrl: ' https://bitbucket.bbh.com/projects/TA/repos/cert-scanner ',
          apiUrl: 'https://bitbucket-api.bbh.com',
          projectKey: 'OTHER',
          repoSlug: 'other',
        }),
      ),
    ).toBe('https://bitbucket.bbh.com/projects/TA/repos/cert-scanner');
  });

  it('builds the Data Center address from the API URL, project key and slug', () => {
    expect(
      bitbucketRepositoryUrl(
        scm({ apiUrl: 'https://bitbucket.bbh.com//', projectKey: 'TA', repoSlug: 'cert-scanner' }),
      ),
    ).toBe('https://bitbucket.bbh.com/projects/TA/repos/cert-scanner');
  });

  it('builds the address of a personal repository from a project key starting with a tilde', () => {
    expect(
      bitbucketRepositoryUrl(
        scm({
          type: 'SERVER',
          apiUrl: 'https://bitbucket.bbh.com',
          projectKey: '~jsmith',
          repoSlug: 'sandbox',
        }),
      ),
    ).toBe('https://bitbucket.bbh.com/users/jsmith/repos/sandbox');
  });

  it('builds the Cloud address from the workspace and slug', () => {
    expect(
      bitbucketRepositoryUrl(
        scm({ type: 'CLOUD', workspace: 'bbh-technology', repoSlug: 'cert-scanner' }),
      ),
    ).toBe('https://bitbucket.org/bbh-technology/cert-scanner');
  });

  it('gives no address while a part of it is missing', () => {
    expect(bitbucketRepositoryUrl(null)).toBeNull();
    expect(bitbucketRepositoryUrl(undefined)).toBeNull();
    expect(bitbucketRepositoryUrl(scm())).toBeNull();
    expect(bitbucketRepositoryUrl(scm({ repositoryUrl: '  ', repoSlug: 'gui' }))).toBeNull();
    expect(
      bitbucketRepositoryUrl(scm({ apiUrl: 'https://bitbucket.bbh.com', repoSlug: 'gui' })),
    ).toBeNull();
    expect(bitbucketRepositoryUrl(scm({ projectKey: 'TA', repoSlug: 'gui' }))).toBeNull();
    expect(bitbucketRepositoryUrl(scm({ type: 'CLOUD', repoSlug: 'gui' }))).toBeNull();
    expect(
      bitbucketRepositoryUrl(scm({ type: 'CLOUD', workspace: 'bbh', apiUrl: 'https://x.bbh.com' })),
    ).toBeNull();
  });
});
