import { ScmSettings } from '../core/models';

export type BitbucketRepository = Pick<
  ScmSettings,
  'repositoryUrl' | 'type' | 'apiUrl' | 'workspace' | 'projectKey' | 'repoSlug'
>;

export function bitbucketRepositoryUrl(scm: BitbucketRepository | null | undefined): string | null {
  const url = clean(scm?.repositoryUrl);
  if (url) {
    return url;
  }
  const slug = clean(scm?.repoSlug);
  if (!scm || !slug) {
    return null;
  }
  if (scm.type === 'CLOUD') {
    const workspace = clean(scm.workspace);
    return workspace ? `https://bitbucket.org/${workspace}/${slug}` : null;
  }
  const base = clean(scm.apiUrl)?.replace(/\/+$/, '');
  const project = clean(scm.projectKey);
  if (!base || !project) {
    return null;
  }
  const owner = project.startsWith('~') ? `users/${project.slice(1)}` : `projects/${project}`;
  return `${base}/${owner}/repos/${slug}`;
}

function clean(value: string | null | undefined): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}
