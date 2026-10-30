/** The settings file names of the DevSecOps library, in the order it looks for them in a repository. */
export const CONFIG_FILE_NAMES = ['devsecops_config.yaml', 'config.yaml'] as const;

/** The name the portal gives the settings file a team commits for a Jenkinsfile without a key. */
export const CONFIG_FILE = CONFIG_FILE_NAMES[0];

/**
 * The file an import reads: the only one chosen or, among several, the first one named exactly as the library looks
 * for it, with the other chosen files. Nothing when no file is chosen, or when none of several has either name.
 */
export function configFileOf(files: readonly File[]): { file: File; skipped: File[] } | undefined {
  const file =
    files.length === 1
      ? files[0]
      : CONFIG_FILE_NAMES.map((name) => files.find((f) => f.name === name)).find(Boolean);
  return file && { file, skipped: files.filter((f) => f !== file) };
}
