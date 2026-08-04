import { Injectable, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { AdminUserSummary, MessagePriority } from "../models/bank.models";

@Injectable({ providedIn: "root" })
export class AdminService {
  private readonly usersApi = "/api/admin/users";
  private readonly messagesApi = "/api/admin/messages";

  private readonly _users = signal<AdminUserSummary[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);
  // Guards against re-fetching on every navigation (both admin pages call loadIfNeeded()) while
  // still allowing a retry after a failure - same convention as MessagesService.
  private loaded = false;

  readonly users = this._users.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  constructor(private http: HttpClient) {}

  loadIfNeeded(): void {
    if (this.loaded || this._loading()) {
      return;
    }
    this._loading.set(true);
    this._error.set(null);
    this.http.get<AdminUserSummary[]>(this.usersApi).subscribe({
      next: (users) => {
        this._users.set(users);
        this._loading.set(false);
        this.loaded = true;
      },
      error: () => {
        this._error.set("Failed to load users.");
        this._loading.set(false);
      },
    });
  }

  getUser(userId: string): AdminUserSummary | undefined {
    return this._users().find((u) => u.id === userId);
  }

  // Optimistic, rolled back on failure - same pattern as MessagesService.markAsRead().
  setActive(userId: string, active: boolean): void {
    const previous = this._users();
    this._users.update((users) =>
      users.map((u) => (u.id === userId ? { ...u, active } : u)),
    );
    this.http.post(`${this.usersApi}/${userId}/active`, { active }).subscribe({
      error: () => this._users.set(previous),
    });
  }

  sendMessage(
    userId: string,
    subject: string,
    body: string,
    priority: MessagePriority,
  ): Observable<void> {
    return this.http.post<void>(this.messagesApi, { ownerId: userId, subject, body, priority });
  }
}
