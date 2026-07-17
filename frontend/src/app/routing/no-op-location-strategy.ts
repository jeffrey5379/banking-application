import { Injectable } from '@angular/core';
import { LocationStrategy } from '@angular/common';

/**
 * Keeps Angular Router fully functional (guards, params, redirects) while
 * never touching the real browser address bar or history stack. Navigation
 * only happens through in-app UI; the URL the user sees never changes.
 *
 * Trade-off: browser back/forward no longer step through in-app views, and
 * a page refresh always reloads whatever URL is actually in the address bar
 * (never the last-viewed in-app route).
 */
@Injectable()
export class NoopLocationStrategy extends LocationStrategy {
  private currentPath = '';
  private currentState: unknown = null;

  override onPopState(): void {
    // No history entries are ever pushed, so popstate never fires for
    // in-app navigation; nothing to react to here.
  }

  override getBaseHref(): string {
    return '';
  }

  override path(): string {
    return this.currentPath;
  }

  override getState(): unknown {
    return this.currentState;
  }

  override prepareExternalUrl(internal: string): string {
    return internal;
  }

  override pushState(state: unknown, _title: string, url: string, queryParams: string): void {
    this.currentPath = url + (queryParams ? '?' + queryParams : '');
    this.currentState = state;
  }

  override replaceState(state: unknown, _title: string, url: string, queryParams: string): void {
    this.currentPath = url + (queryParams ? '?' + queryParams : '');
    this.currentState = state;
  }

  override forward(): void {}

  override back(): void {}

  override historyGo(_relativePosition: number): void {}
}
