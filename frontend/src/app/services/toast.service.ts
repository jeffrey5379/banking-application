import { Injectable, signal } from '@angular/core';

export interface ToastItem {
  id: number;
  prefix: string;
  amountText: string;
  suffix: string;
}

const TOAST_DURATION_MS = 30000;

// Plain UI-transient state, not app domain state - doesn't belong in the NgRx store, which is
// why this is a simple signal-backed service rather than another feature slice.
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 0;
  private readonly _toasts = signal<ToastItem[]>([]);
  readonly toasts = this._toasts.asReadonly();

  show(prefix: string, amountText: string, suffix: string): void {
    const id = this.nextId++;
    this._toasts.update((toasts) => [...toasts, { id, prefix, amountText, suffix }]);
    setTimeout(() => this.dismiss(id), TOAST_DURATION_MS);
  }

  dismiss(id: number): void {
    this._toasts.update((toasts) => toasts.filter((t) => t.id !== id));
  }
}
