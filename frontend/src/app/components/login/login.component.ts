import { Component, ChangeDetectionStrategy } from "@angular/core";

import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { AuthService } from "../../services/auth.service";

@Component({
  selector: "app-login",
  imports: [FormsModule],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <div class="auth-logo">
          <svg width="36" height="36" viewBox="0 0 32 32" fill="none">
            <ellipse cx="16" cy="13" rx="7" ry="6" fill="#0e7490" />
            <circle cx="13" cy="12" r="1.2" fill="white" />
            <circle cx="19" cy="12" r="1.2" fill="white" />
            <path
              d="M9 18 C7.5 20 8 23 9.5 24"
              stroke="#0e7490"
              stroke-width="1.6"
              stroke-linecap="round"
              fill="none"
            />
            <path
              d="M12 19.5 C11 22 11.5 24.5 13 25.5"
              stroke="#0e7490"
              stroke-width="1.6"
              stroke-linecap="round"
              fill="none"
            />
            <path
              d="M16 20 C16 23 16 25 16 27"
              stroke="#0e7490"
              stroke-width="1.6"
              stroke-linecap="round"
              fill="none"
            />
            <path
              d="M20 19.5 C21 22 20.5 24.5 19 25.5"
              stroke="#0e7490"
              stroke-width="1.6"
              stroke-linecap="round"
              fill="none"
            />
            <path
              d="M23 18 C24.5 20 24 23 22.5 24"
              stroke="#0e7490"
              stroke-width="1.6"
              stroke-linecap="round"
              fill="none"
            />
          </svg>
          <span>Octopus Bank</span>
        </div>

        <div class="auth-tabs">
          <button
            [class.active]="mode === 'login'"
            (click)="switchMode('login')"
          >
            Sign In
          </button>
          <button
            [class.active]="mode === 'register'"
            (click)="switchMode('register')"
          >
            Register
          </button>
        </div>

        @if (error) {
          <div class="error-banner">{{ error }}</div>
        }

        @if (mode === "login") {
          <form (ngSubmit)="onLogin()" #loginForm="ngForm">
            <div class="form-group">
              <label class="form-label">Username</label>
              <input
                class="form-input"
                [(ngModel)]="username"
                name="username"
                placeholder="Enter username"
                autocomplete="username"
                required
              />
            </div>
            <div class="form-group" style="margin-bottom:20px">
              <label class="form-label">Password</label>
              <input
                class="form-input"
                type="password"
                [(ngModel)]="password"
                name="password"
                placeholder="Enter password"
                autocomplete="current-password"
                required
              />
            </div>
            <button
              class="btn btn-primary auth-submit"
              type="submit"
              [disabled]="loading"
            >
              {{ loading ? "Signing in…" : "Sign In" }}
            </button>
          </form>
        }

        @if (mode === "register") {
          <form
            (ngSubmit)="onRegister(registerForm)"
            #registerForm="ngForm"
            novalidate
          >
            <div class="form-group">
              <label class="form-label">Username</label>
              <input
                class="form-input"
                [(ngModel)]="username"
                name="username"
                #usernameField="ngModel"
                placeholder="Choose a username"
                autocomplete="username"
                required
                minlength="4"
                [class.input-invalid]="
                  usernameField.invalid &&
                  (usernameField.touched || registerForm.submitted)
                "
              />
              @if (
                usernameField.invalid &&
                (usernameField.touched || registerForm.submitted)
              ) {
                <span class="field-error">
                  {{
                    usernameField.errors?.["required"]
                      ? "Username is required."
                      : "At least 4 characters required."
                  }}
                </span>
              }
            </div>
            <div class="form-group">
              <label class="form-label">Email</label>
              <input
                class="form-input"
                type="email"
                [(ngModel)]="email"
                name="email"
                #emailField="ngModel"
                placeholder="your@email.com"
                autocomplete="email"
                required
                email
                [class.input-invalid]="
                  emailField.invalid &&
                  (emailField.touched || registerForm.submitted)
                "
              />
              @if (
                emailField.invalid &&
                (emailField.touched || registerForm.submitted)
              ) {
                <span class="field-error">
                  {{
                    emailField.errors?.["required"]
                      ? "Email is required."
                      : "Enter a valid email address."
                  }}
                </span>
              }
            </div>
            <div class="form-group" style="margin-bottom:20px">
              <label class="form-label">Password</label>
              <input
                class="form-input"
                type="password"
                [(ngModel)]="password"
                name="password"
                #passwordField="ngModel"
                placeholder="Choose a password"
                autocomplete="new-password"
                required
                minlength="4"
                [class.input-invalid]="
                  passwordField.invalid &&
                  (passwordField.touched || registerForm.submitted)
                "
              />
              @if (
                passwordField.invalid &&
                (passwordField.touched || registerForm.submitted)
              ) {
                <span class="field-error">
                  {{
                    passwordField.errors?.["required"]
                      ? "Password is required."
                      : "At least 4 characters required."
                  }}
                </span>
              }
            </div>
            <button
              class="btn btn-primary auth-submit"
              type="submit"
              [disabled]="loading"
            >
              {{ loading ? "Creating account…" : "Create Account" }}
            </button>
          </form>
        }
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.Eager,
  styles: [
    `
      .auth-container {
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 24px;
        background: var(--surface);
      }
      .auth-card {
        background: var(--surface-elevated);
        border: 1px solid var(--border);
        border-radius: var(--radius-lg);
        box-shadow: var(--shadow-lg);
        width: 100%;
        max-width: 380px;
        padding: 32px;
      }
      .auth-logo {
        display: flex;
        align-items: center;
        gap: 10px;
        justify-content: center;
        margin-bottom: 28px;
        font-family: "Space Grotesk", sans-serif;
        font-weight: 700;
        font-size: 17px;
        color: var(--ink);
      }
      .auth-tabs {
        display: flex;
        background: var(--surface);
        border: 1px solid var(--border);
        border-radius: var(--radius-sm);
        padding: 3px;
        margin-bottom: 24px;
      }
      .auth-tabs button {
        flex: 1;
        padding: 7px;
        border: none;
        background: none;
        border-radius: calc(var(--radius-sm) - 1px);
        cursor: pointer;
        font-size: 13px;
        font-weight: 500;
        color: var(--ink-muted);
        font-family: inherit;
        transition: all 0.15s;
      }
      .auth-tabs button.active {
        background: var(--surface-elevated);
        color: var(--ink);
        box-shadow: var(--shadow-sm);
      }
      .auth-submit {
        width: 100%;
        justify-content: center;
        padding: 10px;
        font-size: 14px;
      }
      .auth-submit:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .input-invalid {
        border-color: var(--danger) !important;
      }
      .input-invalid:focus {
        box-shadow: 0 0 0 3px rgba(207, 34, 46, 0.12) !important;
      }
      .field-error {
        font-size: 12px;
        color: var(--danger);
        margin-top: 4px;
        display: block;
      }
    `,
  ],
})
export class LoginComponent {
  mode: "login" | "register" = "login";
  username = "";
  email = "";
  password = "";
  loading = false;
  error = "";

  constructor(
    private authService: AuthService,
    private router: Router,
  ) {}

  switchMode(mode: "login" | "register") {
    this.mode = mode;
    this.error = "";
    this.password = "";
  }

  onLogin() {
    if (!this.username || !this.password) {
      this.error = "Please fill in all fields.";
      return;
    }
    this.loading = true;
    this.error = "";
    this.authService.login(this.username, this.password).subscribe({
      next: () => this.router.navigate(["/accounts"]),
      error: (err) => {
        this.error =
          err.status === 401
            ? "Invalid username or password."
            : "Login failed. Please try again.";
        this.loading = false;
      },
    });
  }

  onRegister(form: any) {
    if (form.invalid) return;
    this.loading = true;
    this.error = "";
    this.authService
      .register(this.username, this.email, this.password)
      .subscribe({
        next: () => this.router.navigate(["/accounts"]),
        error: (err) => {
          this.error = err.error?.message || "Registration failed.";
          this.loading = false;
        },
      });
  }
}
