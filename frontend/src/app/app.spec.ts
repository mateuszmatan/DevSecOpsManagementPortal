import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { APP_NAME } from '@common/core/app-name';
import { text } from '@common/testing/dom';
import { App } from './app';
import { SECTIONS } from './core/sections';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  let fixture: ComponentFixture<App>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: '**', component: Blank }]),
        { provide: APP_NAME, useValue: 'DevSecOps Management Portal' },
      ],
    });
    fixture = TestBed.createComponent(App);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const links = () => [...page().querySelectorAll<HTMLAnchorElement>('nav.menu a')];
  const active = () =>
    links()
      .filter((link) => link.classList.contains('active'))
      .map(text);

  it('names the portal in its header and links only the DevSecOps sections', () => {
    expect(page().querySelector('.brand-name')?.textContent).toBe(
      'BBH DevSecOps Management Portal',
    );
    expect(links().map(text)).toEqual([
      'Pipelines',
      'Self-service',
      'Pipeline Monitoring',
      'Admin',
    ]);
    expect(links().map((link) => link.getAttribute('href'))).toEqual([
      '/pipelines',
      '/self-service',
      '/monitoring',
      '/admin',
    ]);
    expect(text(page())).not.toContain('Beadle');
    expect(text(page())).not.toContain('Change Evidence');
  });

  it('gives every section the full DevSecOps name as its page heading', () => {
    expect(SECTIONS.map((section) => [section.heading, section.description])).toEqual([
      [
        'DevSecOps Pipelines',
        "Every automated build, test and security pipeline of your department's products. Open one to see its key, its Jenkinsfile and its latest runs.",
      ],
      [
        'DevSecOps Self-service',
        'Set up the DevSecOps pipelines of your product, or change them, in five guided steps. No DevSecOps knowledge needed.',
      ],
      [
        'DevSecOps Pipeline Monitoring',
        'How the pipelines of every product are doing: whether their latest runs passed, and how often and how safely changes reach production.',
      ],
      [
        'DevSecOps Admin',
        'Set up the portal for everyone: departments, products and their services, what a new service gets, and the settings every pipeline shares.',
      ],
    ]);
  });

  it.each([
    ['/monitoring', 'Pipeline Monitoring'],
    ['/monitoring/products/1', 'Pipeline Monitoring'],
    ['/admin/products/2/edit', 'Admin'],
    ['/pipelines/100', 'Pipelines'],
    ['/self-service', 'Self-service'],
  ])('marks the section of %s', async (url, label) => {
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();

    expect(active()).toEqual([label]);
  });
});
