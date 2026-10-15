import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Router, Routes, provideRouter } from '@angular/router';
import { AdminPage } from '@common/admin/admin-page';
import { routes } from './app.routes';
import { DEVSECOPS_ADMIN } from './core/sections';
import { PipelineDepartments } from './departments/pipeline-departments';
import { MonitoringOverview } from './monitoring/monitoring-overview';
import { PipelineMonitoringPage } from './monitoring/pipeline-monitoring';
import { ProductMonitoringPage } from './monitoring/product-monitoring';
import { PipelineList } from './pipelines/pipeline-list';
import { PipelinePage } from './pipelines/pipeline-page';
import { ProductDetail } from './products/product-detail';
import { ProductEditor } from './products/product-editor';
import { ProductList } from './products/product-list';
import { SelfService } from './self-service/self-service';
import { GlobalSettingsPage } from './settings/global-settings';
import { ServiceTemplatePage } from './settings/service-template-page';

@Component({ template: '' })
class Blank {}

const stubbed = (list: Routes): Routes =>
  list.map(({ loadComponent, canDeactivate, children, ...route }) => ({
    ...route,
    ...(loadComponent ? { component: Blank } : {}),
    ...(children ? { children: stubbed(children) } : {}),
  }));

describe('routes', () => {
  const flattened = (list: Routes, parent = ''): [string, Route][] =>
    list.flatMap((route) => {
      const path = [parent, route.path].filter(Boolean).join('/');
      return [[path, route] as [string, Route], ...flattened(route.children ?? [], path)];
    });

  it('loads the page of every section lazily', async () => {
    const pages = await Promise.all(
      flattened(routes)
        .filter(([, route]) => route.loadComponent)
        .map(async ([path, route]) => [path, await route.loadComponent!()]),
    );

    expect(Object.fromEntries(pages)).toEqual({
      pipelines: PipelineList,
      'pipelines/:id': PipelinePage,
      'self-service': SelfService,
      monitoring: MonitoringOverview,
      'monitoring/products/:id': ProductMonitoringPage,
      'monitoring/pipelines/:id': PipelineMonitoringPage,
      'admin/products/new': ProductEditor,
      'admin/products/:id': ProductDetail,
      'admin/products/:id/edit': ProductEditor,
      admin: AdminPage,
      'admin/departments': PipelineDepartments,
      'admin/products': ProductList,
      'admin/template': ServiceTemplatePage,
      'admin/settings': GlobalSettingsPage,
    });
  });

  it('gives the admin area its tabs', () => {
    expect(routes.find((route) => route.path === 'admin')?.data).toEqual({ area: DEVSECOPS_ADMIN });
  });

  it('guards the editors against leaving with unsaved changes', () => {
    expect(
      flattened(routes)
        .filter(([, route]) => route.canDeactivate)
        .map(([path]) => path),
    ).toEqual([
      'self-service',
      'admin/products/new',
      'admin/products/:id/edit',
      'admin/template',
      'admin/settings',
    ]);
  });

  it.each([
    ['/', '/monitoring'],
    ['/admin', '/admin/products'],
    ['/products', '/admin/products'],
    ['/products/new', '/admin/products/new'],
    ['/products/5/edit', '/admin/products/5/edit'],
    ['/settings', '/admin/settings'],
    ['/evidence', '/monitoring'],
    ['/beadle/changes', '/monitoring'],
  ])('sends %s to %s', async (from, to) => {
    TestBed.configureTestingModule({ providers: [provideRouter(stubbed(routes))] });
    const router = TestBed.inject(Router);

    await router.navigateByUrl(from);

    expect(router.url).toBe(to);
  });
});
