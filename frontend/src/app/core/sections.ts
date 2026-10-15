import { AdminArea, PortalSection, PortalTab } from '@common/core/sections';

export const PIPELINES: PortalSection = {
  path: '/pipelines',
  label: 'Pipelines',
  heading: 'DevSecOps Pipelines',
  description:
    "Every automated build, test and security pipeline of your department's products. Open one to see its key, its Jenkinsfile and its latest runs.",
};

export const SELF_SERVICE: PortalSection = {
  path: '/self-service',
  label: 'Self-service',
  heading: 'DevSecOps Self-service',
  description:
    'Set up the DevSecOps pipelines of your product, or change them, in five guided steps. No DevSecOps knowledge needed.',
};

export const MONITORING: PortalSection = {
  path: '/monitoring',
  label: 'Pipeline Monitoring',
  heading: 'DevSecOps Pipeline Monitoring',
  description:
    'How the pipelines of every product are doing: whether their latest runs passed, and how often and how safely changes reach production.',
};

export const ADMIN: PortalSection = {
  path: '/admin',
  label: 'Admin',
  heading: 'DevSecOps Admin',
  description:
    'Set up the portal for everyone: departments, products and their services, what a new service gets, and the settings every pipeline shares.',
};

export const SECTIONS: readonly PortalSection[] = [PIPELINES, SELF_SERVICE, MONITORING, ADMIN];

export const ADMIN_DEPARTMENTS: PortalTab = { path: '/admin/departments', label: 'Departments' };
export const ADMIN_PRODUCTS: PortalTab = { path: '/admin/products', label: 'Products' };
export const ADMIN_TEMPLATE: PortalTab = { path: '/admin/template', label: 'Service template' };
export const ADMIN_SETTINGS: PortalTab = { path: '/admin/settings', label: 'Library defaults' };

export const DEVSECOPS_ADMIN: AdminArea = {
  section: ADMIN,
  tabs: [ADMIN_DEPARTMENTS, ADMIN_PRODUCTS, ADMIN_TEMPLATE, ADMIN_SETTINGS],
};

export function adminProduct(id: number | string, ...rest: string[]): (string | number)[] {
  return [ADMIN_PRODUCTS.path, id, ...rest];
}

export function pipelinePage(id: number | string): (string | number)[] {
  return [PIPELINES.path, id];
}
