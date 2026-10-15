import { Injectable } from '@angular/core';

interface PendingNotice {
  readonly productId: number;
  readonly serviceNames: readonly string[];
}

@Injectable({ providedIn: 'root' })
export class GeneratedKeys {
  private pending: PendingNotice | null = null;

  record(productId: number, serviceNames: readonly string[]): void {
    this.pending = serviceNames.length ? { productId, serviceNames: [...serviceNames] } : null;
  }

  take(productId: number): readonly string[] {
    const pending = this.pending;
    this.pending = null;
    return pending?.productId === productId ? pending.serviceNames : [];
  }
}
