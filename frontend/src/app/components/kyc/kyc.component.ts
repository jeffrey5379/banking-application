import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
} from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule, NgForm } from "@angular/forms";
import { Router } from "@angular/router";
import { map, switchMap } from "rxjs";
import { KycService } from "../../services/kyc.service";
import {
  IssuingCountry,
  KycDocumentType,
  KycStatusResponse,
} from "../../models/bank.models";

interface CountryOption {
  value: IssuingCountry;
  label: string;
}

const COUNTRY_OPTIONS: CountryOption[] = [
  { value: "GERMANY", label: "Germany" },
  { value: "FRANCE", label: "France" },
  { value: "ITALY", label: "Italy" },
  { value: "SPAIN", label: "Spain" },
  { value: "NETHERLANDS", label: "Netherlands" },
  { value: "PORTUGAL", label: "Portugal" },
  { value: "POLAND", label: "Poland" },
  { value: "UNITED_KINGDOM", label: "United Kingdom" },
  { value: "SWITZERLAND", label: "Switzerland" },
  { value: "SWEDEN", label: "Sweden" },
  { value: "UNITED_STATES", label: "United States" },
  { value: "CANADA", label: "Canada" },
  { value: "AUSTRALIA", label: "Australia" },
  { value: "JAPAN", label: "Japan" },
];

@Component({
  selector: "app-kyc",
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1>Identity Verification</h1>
        </div>
      </div>

      @if (loadingStatus()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <span class="text-muted text-sm">Loading verification status…</span>
        </div>
      }

      @if (!loadingStatus() && status()) {
        <div class="card status-card">
          <div class="status-row">
            <span class="text-muted text-sm">Status</span>
            <span class="badge" [class]="statusBadgeClass()">{{
              statusLabel()
            }}</span>
          </div>
          @if (status()!.status === "VERIFIED") {
            <p class="text-muted text-sm" style="margin-top:8px">
              {{ status()!.firstName }} {{ status()!.lastName }} — all your
              documents verified.
            </p>
            <button
              class="btn btn-primary"
              style="margin-top:12px"
              (click)="goToAccounts()"
            >
              Go to Accounts
            </button>
          }
        </div>
      }

      @if (
        !loadingStatus() &&
        status() &&
        status()!.status !== "VERIFIED" &&
        !hasSubmittedIdentity()
      ) {
        <div class="card identity-form-card" style="margin-top:16px">
          <form
            (ngSubmit)="onSubmit(identityForm)"
            #identityForm="ngForm"
            novalidate
          >
            <div class="form-group">
              <label class="form-label">First name</label>
              <input
                class="form-input"
                [(ngModel)]="firstName"
                name="firstName"
                #firstNameField="ngModel"
                placeholder="Enter your first name"
                required
                [class.input-invalid]="
                  firstNameField.invalid &&
                  (firstNameField.touched || identityForm.submitted)
                "
              />
              <div class="field-hint-slot">
                @if (
                  firstNameField.invalid &&
                  (firstNameField.touched || identityForm.submitted)
                ) {
                  <span class="field-hint field-hint-error"
                    >First name is required.</span
                  >
                }
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Last name</label>
              <input
                class="form-input"
                [(ngModel)]="lastName"
                name="lastName"
                #lastNameField="ngModel"
                placeholder="Enter your last name"
                required
                [class.input-invalid]="
                  lastNameField.invalid &&
                  (lastNameField.touched || identityForm.submitted)
                "
              />
              <div class="field-hint-slot">
                @if (
                  lastNameField.invalid &&
                  (lastNameField.touched || identityForm.submitted)
                ) {
                  <span class="field-hint field-hint-error"
                    >Last name is required.</span
                  >
                }
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">Issuing country</label>
              <select
                class="form-select"
                [(ngModel)]="issuingCountry"
                name="issuingCountry"
                required
              >
                @for (country of countryOptions; track country.value) {
                  <option [ngValue]="country.value">{{ country.label }}</option>
                }
              </select>
            </div>

            <div class="form-group" style="margin-bottom:8px">
              <label class="form-label">Document number</label>
              <input
                class="form-input"
                [(ngModel)]="documentNumber"
                name="documentNumber"
                #documentNumberField="ngModel"
                placeholder="ID card or passport number"
                required
                [class.input-invalid]="
                  documentNumberField.invalid &&
                  (documentNumberField.touched || identityForm.submitted)
                "
              />
              <div class="field-hint-slot">
                @if (
                  documentNumberField.invalid &&
                  (documentNumberField.touched || identityForm.submitted)
                ) {
                  <span class="field-hint field-hint-error"
                    >Document number is required.</span
                  >
                }
              </div>
            </div>

            @if (error()) {
              <div class="error-banner">{{ error() }}</div>
            }

            <div class="form-actions">
              <button
                class="btn btn-ghost"
                type="button"
                (click)="goToAccounts()"
              >
                ← Back
              </button>
              <button
                class="btn btn-primary"
                type="submit"
                [disabled]="submitting()"
              >
                {{
                  submitting() ? "Submitting…" : error() ? "Retry" : "Continue"
                }}
              </button>
            </div>
          </form>
        </div>
      }

      @if (
        !loadingStatus() &&
        status() &&
        status()!.status !== "VERIFIED" &&
        hasSubmittedIdentity()
      ) {
        <div class="card identity-form-card" style="margin-top:16px">
          <h3 style="margin-top:0">Upload documents</h3>
          <p class="text-muted text-sm" style="margin-top:4px">
            Upload a clear photo of your ID document and a selfie to complete
            verification.
          </p>

          <div class="upload-item">
            <label class="form-label">ID document photo</label>
            <div class="upload-row">
              <label
                class="btn btn-ghost btn-sm file-upload-btn"
                [class.file-upload-btn-disabled]="
                  isUploaded('ID_DOCUMENT') || uploadingType() !== null
                "
              >
                Choose File
                <input
                  type="file"
                  accept="image/*"
                  class="file-input-hidden"
                  (change)="onFileSelected($event, 'ID_DOCUMENT')"
                  [disabled]="
                    isUploaded('ID_DOCUMENT') || uploadingType() !== null
                  "
                />
              </label>
              @if (isUploaded("ID_DOCUMENT")) {
                <span class="field-hint field-hint-success">Uploaded</span>
              } @else if (uploadingType() === "ID_DOCUMENT") {
                <span class="field-hint">Uploading…</span>
              }
            </div>
          </div>

          <div class="upload-item">
            <label class="form-label">Selfie</label>
            <div class="upload-row">
              <label
                class="btn btn-ghost btn-sm file-upload-btn"
                [class.file-upload-btn-disabled]="
                  isUploaded('SELFIE') || uploadingType() !== null
                "
              >
                Choose File
                <input
                  type="file"
                  accept="image/*"
                  class="file-input-hidden"
                  (change)="onFileSelected($event, 'SELFIE')"
                  [disabled]="isUploaded('SELFIE') || uploadingType() !== null"
                />
              </label>
              @if (isUploaded("SELFIE")) {
                <span class="field-hint field-hint-success">Uploaded</span>
              } @else if (uploadingType() === "SELFIE") {
                <span class="field-hint">Uploading…</span>
              }
            </div>
          </div>

          @if (error()) {
            <div class="error-banner" style="margin-top:12px">
              {{ error() }}
            </div>
          }

          <div class="form-actions" style="margin-top:16px">
            <button
              class="btn btn-ghost"
              type="button"
              (click)="goToAccounts()"
            >
              ← Back
            </button>
          </div>
        </div>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .page-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        margin-bottom: 24px;
      }
      .status-card {
        padding: 20px;
      }
      .status-row {
        display: flex;
        align-items: center;
        gap: 10px;
      }
      .identity-form-card {
        padding: 20px 24px;
      }
      .form-actions {
        display: flex;
        gap: 10px;
      }
      .upload-item {
        margin-bottom: 16px;
      }
      .upload-row {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .file-upload-btn {
        position: relative;
      }
      .file-upload-btn-disabled {
        opacity: 0.5;
        cursor: default;
      }
      .file-upload-btn-disabled:hover {
        background: transparent;
        border-color: var(--border);
      }
      .file-input-hidden {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border: 0;
      }
      .field-hint-slot {
        display: block;
        min-height: 18px;
        margin-top: 4px;
      }
      .field-hint {
        display: block;
        font-size: 12px;
        color: var(--ink-muted);
      }
      .field-hint-error {
        color: var(--danger);
      }
      .field-hint-success {
        color: var(--success);
      }
      .input-invalid {
        border-color: var(--danger) !important;
      }
      .badge-success {
        background: var(--success-light);
        color: var(--success);
      }
      .badge-warning {
        background: var(--warning-light);
        color: var(--warning);
      }
      .badge-danger {
        background: var(--danger-light);
        color: var(--danger);
      }
      .badge-muted {
        background: var(--surface-inset);
        color: var(--ink-muted);
      }
    `,
  ],
})
export class KycComponent implements OnInit {
  private kycService = inject(KycService);
  private router = inject(Router);

  countryOptions = COUNTRY_OPTIONS;

  status = signal<KycStatusResponse | null>(null);
  loadingStatus = signal(true);
  submitting = signal(false);
  uploadingType = signal<KycDocumentType | null>(null);
  error = signal("");

  firstName = "";
  lastName = "";
  issuingCountry: IssuingCountry = "GERMANY";
  documentNumber = "";

  ngOnInit() {
    this.kycService.getStatus().subscribe({
      next: (res) => {
        this.status.set(res);
        this.loadingStatus.set(false);
      },
      error: () => {
        this.error.set("Could not load verification status.");
        this.loadingStatus.set(false);
      },
    });
  }

  onSubmit(form: NgForm) {
    if (form.invalid) return;
    this.submitting.set(true);
    this.error.set("");
    this.kycService
      .submitIdentity({
        firstName: this.firstName,
        lastName: this.lastName,
        issuingCountry: this.issuingCountry,
        documentNumber: this.documentNumber,
      })
      .subscribe({
        next: (res) => {
          this.status.set(res);
          this.submitting.set(false);
        },
        error: (err) => {
          this.error.set(
            err.error?.message || "Verification failed. Please try again.",
          );
          this.submitting.set(false);
        },
      });
  }

  onFileSelected(event: Event, type: KycDocumentType) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploadingType.set(type);
    this.error.set("");

    this.kycService
      .requestUploadUrl(type)
      .pipe(
        switchMap((res) =>
          this.kycService
            .uploadFile(res.uploadUrl, file)
            .pipe(map(() => res.documentId)),
        ),
        switchMap((documentId) => this.kycService.completeUpload(documentId)),
      )
      .subscribe({
        next: (res) => {
          this.status.set(res);
          this.uploadingType.set(null);
          input.value = "";
        },
        error: (err) => {
          this.error.set(
            err.error?.message || "Upload failed. Please try again.",
          );
          this.uploadingType.set(null);
          input.value = "";
        },
      });
  }

  hasSubmittedIdentity(): boolean {
    return !!this.status()?.firstName;
  }

  isUploaded(type: KycDocumentType): boolean {
    return (
      this.status()?.documents.some((d) => d.type === type && d.uploaded) ??
      false
    );
  }

  statusLabel(): string {
    switch (this.status()?.status) {
      case "VERIFIED":
        return "Verified";
      case "PENDING":
        return "Pending";
      case "REJECTED":
        return "Rejected";
      default:
        return "Not started";
    }
  }

  statusBadgeClass(): string {
    switch (this.status()?.status) {
      case "VERIFIED":
        return "badge-success";
      case "PENDING":
        return "badge-warning";
      case "REJECTED":
        return "badge-danger";
      default:
        return "badge-muted";
    }
  }

  countryLabel(country: IssuingCountry | undefined): string {
    return this.countryOptions.find((c) => c.value === country)?.label ?? "";
  }

  goToAccounts() {
    this.router.navigate(["/accounts"]);
  }
}
