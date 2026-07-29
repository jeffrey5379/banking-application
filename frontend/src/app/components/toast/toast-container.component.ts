import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { ToastService } from '../../services/toast.service';

// Rendered once from AppComponent (the persistent root shell), so it shows regardless of which
// page/route is currently active - the whole point being that a balance update from someone
// else's action can land while the user is anywhere in the app.
@Component({
  selector: 'app-toast-container',
  standalone: true,
  template: `
    <div class="toast-stack">
      @for (toast of toasts(); track toast.id; let i = $index; let count = $count) {
        <div
          class="toast"
          [style.transform]="offsetFor(count - 1 - i)"
          [style.z-index]="1000 - (count - 1 - i)"
        >
          <span class="toast-message">
            {{ toast.prefix }}
            <span class="toast-amount">{{ toast.amountText }}</span>
            {{ toast.suffix }}
          </span>
          <button class="toast-close" type="button" aria-label="Dismiss" (click)="dismiss(toast.id)">
            ×
          </button>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .toast-stack {
        position: fixed;
        left: 20px;
        bottom: 20px;
        z-index: 1000;
        pointer-events: none;
      }
      .toast {
        position: absolute;
        left: 0;
        bottom: 0;
        display: flex;
        align-items: flex-start;
        gap: 10px;
        width: max-content;
        max-width: 320px;
        padding: 12px 16px;
        background: var(--accent);
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-md);
        color: white;
        font-size: 13px;
        line-height: 1.4;
        pointer-events: auto;
        transition: transform 0.2s ease;
      }
      .toast-message {
        flex: 1;
      }
      .toast-amount {
        font-weight: 700;
      }
      .toast-close {
        flex-shrink: 0;
        border: none;
        background: transparent;
        color: white;
        opacity: 0.8;
        font-size: 16px;
        line-height: 1;
        cursor: pointer;
        padding: 0;
      }
      .toast-close:hover {
        opacity: 1;
      }
    `,
  ],
})
export class ToastContainerComponent {
  private toastService = inject(ToastService);

  toasts = this.toastService.toasts;

  // Older toasts (higher `position`) sit further up and to the right, behind the newest one -
  // which stays anchored at the bottom-left corner on top of the stack.
  offsetFor(position: number): string {
    const step = 10;
    return `translate(${position * step}px, ${-position * step}px)`;
  }

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }
}
