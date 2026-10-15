import { Routes } from '@angular/router';
import { unsavedChangesGuard } from '@common/core/unsaved-changes';
import {
  ADMIN,
  ADMIN_DEPARTMENTS,
  ADMIN_PRODUCTS,
  ADMIN_SETTINGS,
  ADMIN_TEMPLATE,
  DEVSECOPS_ADMIN,
  MONITORING,
  PIPELINES,
  SELF_SERVICE,
} from './core/sections';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'monitoring' },
  {
    path: 'pipelines',
    title: PIPELINES.heading,
    loadComponent: () => import('./pipelines/pipeline-list').then((m) => m.PipelineList),
  },
  {
    path: 'pipelines/:id',
    title: 'Pipeline',
    loadComponent: () => import('./pipelines/pipeline-page').then((m) => m.PipelinePage),
  },
  {
    path: 'self-service',
    title: SELF_SERVICE.heading,
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./self-service/self-service').then((m) => m.SelfService),
  },
  {
    path: 'monitoring',
    title: MONITORING.heading,
    loadComponent: () =>
      import('./monitoring/monitoring-overview').then((m) => m.MonitoringOverview),
  },
  {
    path: 'monitoring/products/:id',
    title: 'Product pipelines',
    loadComponent: () =>
      import('./monitoring/product-monitoring').then((m) => m.ProductMonitoringPage),
  },
  {
    path: 'monitoring/pipelines/:id',
    title: 'Pipeline details',
    loadComponent: () =>
      import('./monitoring/pipeline-monitoring').then((m) => m.PipelineMonitoringPage),
  },
  {
    path: 'admin/products/new',
    title: 'Add product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'admin/products/:id',
    title: 'Product',
    loadComponent: () => import('./products/product-detail').then((m) => m.ProductDetail),
  },
  {
    path: 'admin/products/:id/edit',
    title: 'Edit product',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./products/product-editor').then((m) => m.ProductEditor),
  },
  {
    path: 'admin',
    loadComponent: () => import('@common/admin/admin-page').then((m) => m.AdminPage),
    data: { area: DEVSECOPS_ADMIN },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'products' },
      {
        path: 'departments',
        title: `${ADMIN_DEPARTMENTS.label} · ${ADMIN.heading}`,
        loadComponent: () =>
          import('./departments/pipeline-departments').then((m) => m.PipelineDepartments),
      },
      {
        path: 'products',
        title: `${ADMIN_PRODUCTS.label} · ${ADMIN.heading}`,
        loadComponent: () => import('./products/product-list').then((m) => m.ProductList),
      },
      {
        path: 'template',
        title: `${ADMIN_TEMPLATE.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./settings/service-template-page').then((m) => m.ServiceTemplatePage),
      },
      {
        path: 'settings',
        title: `${ADMIN_SETTINGS.label} · ${ADMIN.heading}`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () => import('./settings/global-settings').then((m) => m.GlobalSettingsPage),
      },
    ],
  },
  { path: 'products', redirectTo: 'admin/products' },
  { path: 'settings', redirectTo: 'admin/settings' },
  { path: '**', redirectTo: 'monitoring' },
];
