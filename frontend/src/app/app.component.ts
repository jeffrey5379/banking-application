import { Component, ChangeDetectionStrategy } from "@angular/core";
import { RouterOutlet, RouterLink, Router } from "@angular/router";

import { AuthService } from "./services/auth.service";

@Component({
  selector: "app-root",
  imports: [RouterOutlet, RouterLink],
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
              <button class="btn-logout" (click)="logout()">Sign out</button>
            </nav>
          }
        </div>
      </header>
      <main class="main-content">
        <router-outlet />
      </main>
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
        height: 56px;
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

      .main-content {
        flex: 1;
      }
    `,
  ],
})
export class AppComponent {
  constructor(
    public authService: AuthService,
    private router: Router,
  ) {}

  logout() {
    this.authService.logout().subscribe({
      complete: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login']),
    });
  }
}
