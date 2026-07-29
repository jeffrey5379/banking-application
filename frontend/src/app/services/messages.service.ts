import { Injectable, computed, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Message } from "../models/bank.models";

@Injectable({ providedIn: "root" })
export class MessagesService {
  private readonly api = "/api/notifications/messages";

  private readonly _messages = signal<Message[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);
  // Guards against re-fetching on every navigation (AppComponent calls loadIfNeeded() on every
  // route change to catch a just-completed login) while still allowing a retry after a failure.
  private loaded = false;

  readonly messages = this._messages.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();
  readonly unreadCount = computed(
    () => this._messages().filter((message) => !message.read).length,
  );

  constructor(private http: HttpClient) {}

  loadIfNeeded(): void {
    if (this.loaded || this._loading()) {
      return;
    }
    this._loading.set(true);
    this._error.set(null);
    this.http.get<Message[]>(this.api).subscribe({
      next: (messages) => {
        this._messages.set(messages);
        this._loading.set(false);
        this.loaded = true;
      },
      error: () => {
        this._error.set("Failed to load messages.");
        this._loading.set(false);
      },
    });
  }

  // Called on logout so a next login (possibly as a different user) doesn't briefly show the
  // previous session's messages before the fresh load() completes.
  clear(): void {
    this.loaded = false;
    this._messages.set([]);
    this._error.set(null);
  }

  // Called by NotificationService when a "message-created" SSE event arrives, so a message
  // pushed while the user is already logged in shows up in the list/badge immediately, without
  // waiting for a page reload or re-navigating to /messages. A freshly created message is always
  // unread and newest-first, matching the backend's own list ordering.
  addMessage(message: Message): void {
    if (!this.loaded) {
      // Nothing to prepend onto yet - the upcoming loadIfNeeded() fetch will already include
      // this message, so adding it now would just risk a duplicate once that response lands.
      return;
    }
    this._messages.update((messages) => [message, ...messages]);
  }

  // Optimistic: the list/badge should reflect "read" the instant the user opens a message, not
  // after a round trip. Rolled back if the request fails.
  markAsRead(id: string): void {
    const message = this._messages().find((m) => m.id === id);
    if (!message || message.read) {
      return;
    }
    this.setRead(id, true);
    this.http.post<Message>(`${this.api}/${id}/read`, {}).subscribe({
      error: () => this.setRead(id, false),
    });
  }

  private setRead(id: string, read: boolean): void {
    this._messages.update((messages) =>
      messages.map((message) => (message.id === id ? { ...message, read } : message)),
    );
  }
}
