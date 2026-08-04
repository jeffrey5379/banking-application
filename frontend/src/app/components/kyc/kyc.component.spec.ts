import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { KycComponent } from './kyc.component';
import { KycService } from '../../services/kyc.service';
import { KycStatusResponse } from '../../models/bank.models';

describe('KycComponent', () => {
  let component: KycComponent;
  let fixture: ComponentFixture<KycComponent>;
  let kycService: jest.Mocked<
    Pick<KycService, 'getStatus' | 'submitIdentity' | 'requestUploadUrl' | 'uploadFile' | 'completeUpload'>
  >;
  let router: { navigate: jest.Mock };

  const notStarted: KycStatusResponse = {
    userId: 'u1',
    status: 'NOT_STARTED',
    level: 'NONE',
    documents: [],
  };

  const identitySubmittedNoDocuments: KycStatusResponse = {
    userId: 'u1',
    status: 'NOT_STARTED',
    level: 'NONE',
    firstName: 'Alice',
    lastName: 'Anderson',
    issuingCountry: 'UNITED_KINGDOM',
    documentNumber: 'AA1234567',
    documents: [],
  };

  const identitySubmittedOneDocumentUploaded: KycStatusResponse = {
    ...identitySubmittedNoDocuments,
    documents: [{ id: 'doc-1', type: 'ID_DOCUMENT', uploaded: true, uploadedAt: '2026-01-01T00:00:00' }],
  };

  const bothDocumentsUploadedPending: KycStatusResponse = {
    ...identitySubmittedNoDocuments,
    status: 'PENDING',
    documents: [
      { id: 'doc-1', type: 'ID_DOCUMENT', uploaded: true, uploadedAt: '2026-01-01T00:00:00' },
      { id: 'doc-2', type: 'SELFIE', uploaded: true, uploadedAt: '2026-01-01T00:00:00' },
    ],
  };

  const rejected: KycStatusResponse = {
    userId: 'u1',
    status: 'REJECTED',
    level: 'NONE',
    firstName: 'Alice',
    lastName: 'Anderson',
    issuingCountry: 'UNITED_KINGDOM',
    documentNumber: 'AA1234567',
    rejectionReason: 'Document photo was blurry',
    documents: [
      { id: 'doc-1', type: 'ID_DOCUMENT', uploaded: true, uploadedAt: '2026-01-01T00:00:00' },
      { id: 'doc-2', type: 'SELFIE', uploaded: true, uploadedAt: '2026-01-01T00:00:00' },
    ],
  };

  const verified: KycStatusResponse = {
    userId: 'u1',
    status: 'VERIFIED',
    level: 'BASIC',
    firstName: 'Alice',
    lastName: 'Anderson',
    issuingCountry: 'UNITED_KINGDOM',
    documentNumber: 'AA1234567',
    documents: [
      { id: 'doc-1', type: 'ID_DOCUMENT', uploaded: true, uploadedAt: '2026-01-01T00:00:00' },
      { id: 'doc-2', type: 'SELFIE', uploaded: true, uploadedAt: '2026-01-01T00:00:00' },
    ],
  };

  beforeEach(() => {
    kycService = {
      getStatus: jest.fn(),
      submitIdentity: jest.fn(),
      requestUploadUrl: jest.fn(),
      uploadFile: jest.fn(),
      completeUpload: jest.fn(),
    };
    router = { navigate: jest.fn() };

    TestBed.configureTestingModule({
      imports: [KycComponent],
      providers: [
        { provide: KycService, useValue: kycService },
        { provide: Router, useValue: router },
      ],
    });

    fixture = TestBed.createComponent(KycComponent);
    component = fixture.componentInstance;
  });

  describe('ngOnInit()', () => {
    it('loads the current status and stops the loading spinner', () => {
      kycService.getStatus.mockReturnValue(of(notStarted));

      component.ngOnInit();

      expect(component.status()).toEqual(notStarted);
      expect(component.loadingStatus()).toBe(false);
    });

    it('shows an error and stops loading when the status call fails', () => {
      kycService.getStatus.mockReturnValue(throwError(() => ({})));

      component.ngOnInit();

      expect(component.error()).toBe('Could not load verification status.');
      expect(component.loadingStatus()).toBe(false);
    });
  });

  describe('onSubmit()', () => {
    it('does nothing when the form is invalid', () => {
      component.onSubmit({ invalid: true } as any);

      expect(kycService.submitIdentity).not.toHaveBeenCalled();
    });

    it('submits the entered identity details and stores the resulting status', () => {
      component.firstName = 'Alice';
      component.lastName = 'Anderson';
      component.issuingCountry = 'UNITED_KINGDOM';
      component.documentNumber = 'AA1234567';
      kycService.submitIdentity.mockReturnValue(of(identitySubmittedNoDocuments));

      component.onSubmit({ invalid: false } as any);

      expect(kycService.submitIdentity).toHaveBeenCalledWith({
        firstName: 'Alice',
        lastName: 'Anderson',
        issuingCountry: 'UNITED_KINGDOM',
        documentNumber: 'AA1234567',
      });
      expect(component.status()).toEqual(identitySubmittedNoDocuments);
      expect(component.submitting()).toBe(false);
    });

    it('shows the backend error message on failure', () => {
      component.firstName = 'Alice';
      component.lastName = 'Anderson';
      component.documentNumber = 'AA1234567';
      kycService.submitIdentity.mockReturnValue(
        throwError(() => ({ error: { message: 'Details did not match records' } })),
      );

      component.onSubmit({ invalid: false } as any);

      expect(component.error()).toBe('Details did not match records');
      expect(component.submitting()).toBe(false);
    });

    it('falls back to a generic message when the backend gives no message', () => {
      kycService.submitIdentity.mockReturnValue(throwError(() => ({})));

      component.onSubmit({ invalid: false } as any);

      expect(component.error()).toBe('Verification failed. Please try again.');
    });
  });

  describe('onPhotoCaptured()', () => {
    it('requests an upload URL, uploads the captured photo, completes the upload, and stores the resulting status', () => {
      const file = new File(['content'], 'id-document.jpg', { type: 'image/jpeg' });
      kycService.requestUploadUrl.mockReturnValue(of({ documentId: 'doc-1', uploadUrl: 'https://minio.local/x' }));
      kycService.uploadFile.mockReturnValue(of(undefined));
      kycService.completeUpload.mockReturnValue(of(identitySubmittedOneDocumentUploaded));

      component.onPhotoCaptured(file, 'ID_DOCUMENT');

      expect(kycService.requestUploadUrl).toHaveBeenCalledWith('ID_DOCUMENT');
      expect(kycService.uploadFile).toHaveBeenCalledWith('https://minio.local/x', file);
      expect(kycService.completeUpload).toHaveBeenCalledWith('doc-1');
      expect(component.status()).toEqual(identitySubmittedOneDocumentUploaded);
      expect(component.uploadingType()).toBeNull();
    });

    it('shows the backend error message when requesting the upload URL fails', () => {
      const file = new File(['content'], 'id-document.jpg', { type: 'image/jpeg' });
      kycService.requestUploadUrl.mockReturnValue(
        throwError(() => ({ error: { message: 'Submit your identity details first' } })),
      );

      component.onPhotoCaptured(file, 'ID_DOCUMENT');

      expect(component.error()).toBe('Submit your identity details first');
      expect(component.uploadingType()).toBeNull();
    });

    it('falls back to a generic message when the upload itself fails', () => {
      const file = new File(['content'], 'id-document.jpg', { type: 'image/jpeg' });
      kycService.requestUploadUrl.mockReturnValue(of({ documentId: 'doc-1', uploadUrl: 'https://minio.local/x' }));
      kycService.uploadFile.mockReturnValue(throwError(() => ({})));

      component.onPhotoCaptured(file, 'ID_DOCUMENT');

      expect(component.error()).toBe('Upload failed. Please try again.');
      expect(kycService.completeUpload).not.toHaveBeenCalled();
    });
  });

  describe('isUploaded()', () => {
    it('returns true only for a type present and marked uploaded', () => {
      component.status.set(identitySubmittedOneDocumentUploaded);

      expect(component.isUploaded('ID_DOCUMENT')).toBe(true);
      expect(component.isUploaded('SELFIE')).toBe(false);
    });
  });

  describe('isRejected()', () => {
    it('returns true only when the status is REJECTED', () => {
      component.status.set(rejected);
      expect(component.isRejected()).toBe(true);

      component.status.set(identitySubmittedNoDocuments);
      expect(component.isRejected()).toBe(false);
    });
  });

  describe('goToStep()', () => {
    it('changes the active upload step', () => {
      component.goToStep('SELFIE');
      expect(component.uploadStep()).toBe('SELFIE');

      component.goToStep('ID_DOCUMENT');
      expect(component.uploadStep()).toBe('ID_DOCUMENT');
    });
  });

  describe('upload step syncing', () => {
    it('lands on the ID document step when no document has been uploaded yet', () => {
      kycService.getStatus.mockReturnValue(of(identitySubmittedNoDocuments));

      component.ngOnInit();

      expect(component.uploadStep()).toBe('ID_DOCUMENT');
    });

    it('advances to the selfie step once the ID document is uploaded', () => {
      kycService.getStatus.mockReturnValue(of(identitySubmittedOneDocumentUploaded));

      component.ngOnInit();

      expect(component.uploadStep()).toBe('SELFIE');
    });

    it('stays on the ID document step for a rejected submission, even with both documents already uploaded', () => {
      kycService.getStatus.mockReturnValue(of(rejected));

      component.ngOnInit();

      expect(component.uploadStep()).toBe('ID_DOCUMENT');
    });

    it('advances to the selfie step after the ID document upload completes', () => {
      const file = new File(['content'], 'id-document.jpg', { type: 'image/jpeg' });
      kycService.requestUploadUrl.mockReturnValue(of({ documentId: 'doc-1', uploadUrl: 'https://minio.local/x' }));
      kycService.uploadFile.mockReturnValue(of(undefined));
      kycService.completeUpload.mockReturnValue(of(identitySubmittedOneDocumentUploaded));

      component.onPhotoCaptured(file, 'ID_DOCUMENT');

      expect(component.uploadStep()).toBe('SELFIE');
    });
  });

  describe('statusLabel()', () => {
    it('shows "In progress" once identity details are submitted but the profile is not yet complete', () => {
      component.status.set(identitySubmittedNoDocuments);

      expect(component.statusLabel()).toBe('In progress');
      expect(component.statusBadgeClass()).toBe('badge-warning');
    });

    it('shows "Not started" before any identity details are submitted', () => {
      component.status.set(notStarted);

      expect(component.statusLabel()).toBe('Not started');
      expect(component.statusBadgeClass()).toBe('badge-muted');
    });

    it('shows "Pending" once the profile is complete and awaiting review', () => {
      component.status.set(bothDocumentsUploadedPending);

      expect(component.statusLabel()).toBe('Pending');
      expect(component.statusBadgeClass()).toBe('badge-warning');
    });
  });

  describe('goToAccounts()', () => {
    it('navigates to /accounts', () => {
      component.goToAccounts();

      expect(router.navigate).toHaveBeenCalledWith(['/accounts']);
    });
  });

  describe('template', () => {
    it('shows the identity form and a Back button before identity details are submitted', () => {
      kycService.getStatus.mockReturnValue(of(notStarted));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('input[name="firstName"]')).toBeTruthy();
      expect(compiled.querySelector('app-camera-capture')).toBeFalsy();
      const buttons = Array.from(compiled.querySelectorAll('button')).map((b) => b.textContent?.trim());
      expect(buttons.some((text) => text?.includes('Back'))).toBe(true);
    });

    it('shows only the ID document step, on its own, once identity details are submitted', () => {
      kycService.getStatus.mockReturnValue(of(identitySubmittedNoDocuments));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('input[name="firstName"]')).toBeFalsy();
      expect(compiled.querySelectorAll('app-camera-capture').length).toBe(1);
      expect(compiled.textContent).toContain('ID document photo');
      // The selfie step's own content should not be rendered yet - only its step-indicator label is.
      expect(compiled.textContent).not.toContain('Look directly at the camera');
    });

    it('auto-advances to the selfie step and marks the ID document step done once it is uploaded', () => {
      kycService.getStatus.mockReturnValue(of(identitySubmittedOneDocumentUploaded));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelectorAll('app-camera-capture').length).toBe(1);
      expect(compiled.textContent).toContain('Selfie');
      expect(compiled.querySelector('.step-dot-done')).toBeTruthy();
    });

    it('lets the user step back from the selfie step to review the ID document again', () => {
      kycService.getStatus.mockReturnValue(of(identitySubmittedOneDocumentUploaded));
      fixture.detectChanges();

      component.goToStep('ID_DOCUMENT');
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('ID document photo');
      expect(compiled.textContent).toContain('Uploaded');
    });

    it('hides the upload section and shows a Go to Accounts button once verified', () => {
      kycService.getStatus.mockReturnValue(of(verified));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('app-camera-capture')).toBeFalsy();
      expect(compiled.textContent).toContain('Verified');
      const buttons = Array.from(compiled.querySelectorAll('button')).map((b) => b.textContent?.trim());
      expect(buttons.some((text) => text?.includes('Go to Accounts'))).toBe(true);
    });

    it('shows the rejection reason and re-enables the camera capture for a rejected submission, on both steps', () => {
      kycService.getStatus.mockReturnValue(of(rejected));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Document photo was blurry');
      // Both documents are already marked uploaded=true, but rejection must still allow a
      // fresh capture rather than leaving the user stuck with a disabled camera trigger - on
      // whichever step is currently active.
      let cameraTriggerButtons = Array.from(compiled.querySelectorAll('app-camera-capture button'));
      expect(cameraTriggerButtons.length).toBe(1);
      expect((cameraTriggerButtons[0] as HTMLButtonElement).disabled).toBe(false);

      component.goToStep('SELFIE');
      fixture.detectChanges();

      cameraTriggerButtons = Array.from(compiled.querySelectorAll('app-camera-capture button'));
      expect(cameraTriggerButtons.length).toBe(1);
      expect((cameraTriggerButtons[0] as HTMLButtonElement).disabled).toBe(false);
    });
  });
});
