import { Component, ChangeDetectionStrategy, OnInit, OnDestroy } from "@angular/core";
import { RouterOutlet, RouterLink, Router, NavigationEnd } from "@angular/router";
import { Subscription, filter } from "rxjs";

import { AuthService } from "./services/auth.service";
import { NotificationService } from "./services/notification.service";
import { MessagesService } from "./services/messages.service";
import { ToastContainerComponent } from "./components/toast/toast-container.component";

@Component({
  selector: "app-root",
  imports: [RouterOutlet, RouterLink, ToastContainerComponent],
  template: `
    <div class="app-shell">
      <header class="topbar">
        <div class="topbar-inner">
          <a routerLink="/" class="logo">
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
              <!-- body -->
              <ellipse cx="16" cy="13" rx="7" ry="6" fill="white" />
              <!-- eyes -->
              <circle cx="13" cy="12" r="1.2" fill="#0e7490" />
              <circle cx="19" cy="12" r="1.2" fill="#0e7490" />
              <!-- tentacles -->
              <path
                d="M9 18 C7.5 20 8 23 9.5 24"
                stroke="white"
                stroke-width="1.6"
                stroke-linecap="round"
                fill="none"
              />
              <path
                d="M12 19.5 C11 22 11.5 24.5 13 25.5"
                stroke="white"
                stroke-width="1.6"
                stroke-linecap="round"
                fill="none"
              />
              <path
                d="M16 20 C16 23 16 25 16 27"
                stroke="white"
                stroke-width="1.6"
                stroke-linecap="round"
                fill="none"
              />
              <path
                d="M20 19.5 C21 22 20.5 24.5 19 25.5"
                stroke="white"
                stroke-width="1.6"
                stroke-linecap="round"
                fill="none"
              />
              <path
                d="M23 18 C24.5 20 24 23 22.5 24"
                stroke="white"
                stroke-width="1.6"
                stroke-linecap="round"
                fill="none"
              />
            </svg>
            <span>Octopus Bank</span>
          </a>
          @if (authService.isLoggedIn()) {
            <nav class="topbar-nav">
              <p class="nav-user">
                Glad to see you
                <strong>{{ authService.getUser()?.username }}</strong>
              </p>
              <a routerLink="/messages" class="btn-icon" title="Messages" aria-label="Messages">
                <svg width="18" height="18" viewBox="0 0 20 20" fill="none">
                  <rect
                    x="2"
                    y="4"
                    width="16"
                    height="12"
                    rx="2"
                    stroke="currentColor"
                    stroke-width="1.5"
                  />
                  <path
                    d="M3 5.5l7 5 7-5"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
                @if (messagesService.unreadCount() > 0) {
                  <span class="unread-badge">{{ messagesService.unreadCount() }}</span>
                }
              </a>
              <button class="btn-logout" (click)="logout()">Sign out</button>
            </nav>
          }
        </div>
      </header>
      <main class="main-content">
        <router-outlet />
      </main>
      <app-toast-container />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .app-shell {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
      }

      .topbar {
        position: sticky;
        top: 0;
        z-index: 50;
        background: var(--accent);
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
      }
      .topbar-inner {
        max-width: 900px;
        margin: 0 auto;
        padding: 0 24px;
        height: var(--topbar-height);
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .logo {
        display: flex;
        align-items: center;
        gap: 10px;
        text-decoration: none;
        color: white;
        font-family: "Space Grotesk", sans-serif;
        font-weight: 700;
        font-size: 17px;
      }
      .topbar-nav {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .nav-user {
        font-size: 13px;
        color: rgba(255, 255, 255, 0.8);
        padding: 0 4px;
      }
      .nav-user strong {
        color: white;
      }
      .btn-logout {
        padding: 5px 12px;
        border-radius: var(--radius-sm);
        border: 1px solid rgba(255, 255, 255, 0.35);
        background: rgba(255, 255, 255, 0.12);
        color: white;
        font-size: 13px;
        font-weight: 500;
        cursor: pointer;
        font-family: inherit;
        transition: all 0.15s;
      }
      .btn-logout:hover {
        background: rgba(255, 255, 255, 0.22);
        border-color: rgba(255, 255, 255, 0.55);
      }
      .btn-icon {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        border-radius: var(--radius-sm);
        border: 1px solid rgba(255, 255, 255, 0.35);
        background: rgba(255, 255, 255, 0.12);
        color: white;
        text-decoration: none;
        transition: all 0.15s;
      }
      .btn-icon:hover {
        background: rgba(255, 255, 255, 0.22);
        border-color: rgba(255, 255, 255, 0.55);
      }
      .unread-badge {
        position: absolute;
        top: -5px;
        right: -5px;
        min-width: 16px;
        height: 16px;
        padding: 0 4px;
        border-radius: 8px;
        background: var(--accent);
        color: white;
        font-size: 10px;
        font-weight: 700;
        line-height: 16px;
        text-align: center;
        border: 1px solid white;
      }

      .main-content {
        flex: 1;
      }
    `,
  ],
})
export class AppComponent implements OnInit, OnDestroy {
  private navigationSubscription?: Subscription;

  constructor(
    public authService: AuthService,
    public messagesService: MessagesService,
    private router: Router,
    private notificationService: NotificationService,
  ) {}

  ngOnInit(): void {
    // AppComponent is the persistent root shell (never destroyed/recreated by routing), so this
    // runs once at bootstrap - re-check login state on every navigation too, since that's the
    // only signal available here that a login/logout just happened elsewhere in the app.
    this.syncSessionState();
    this.navigationSubscription = this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.syncSessionState());
  }

  ngOnDestroy(): void {
    this.navigationSubscription?.unsubscribe();
  }

  logout() {
    this.authService.logout().subscribe({
      complete: () => this.onLoggedOut(),
      error: () => this.onLoggedOut(),
    });
  }

  private onLoggedOut(): void {
    this.notificationService.disconnect();
    this.messagesService.clear();
    this.router.navigate(['/login']);
  }

  private syncSessionState(): void {
    if (this.authService.isLoggedIn()) {
      this.notificationService.connect();
      // loadIfNeeded() is a no-op once messages are already loaded (or loading), so this is
      // cheap to call on every navigation - it only does real work right after login.
      this.messagesService.loadIfNeeded();
    } else {
      this.notificationService.disconnect();
    }
  }
}
