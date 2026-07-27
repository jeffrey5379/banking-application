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
    status: 'PENDING',
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

  describe('onFileSelected()', () => {
    function fileInputEvent(file: File | null): Event {
      const input = document.createElement('input');
      input.type = 'file';
      if (file) {
        Object.defineProperty(input, 'files', { value: [file] });
      }
      return { target: input } as unknown as Event;
    }

    it('does nothing when no file was chosen', () => {
      component.onFileSelected(fileInputEvent(null), 'ID_DOCUMENT');

      expect(kycService.requestUploadUrl).not.toHaveBeenCalled();
    });

    it('requests an upload URL, uploads the file, completes the upload, and stores the resulting status', () => {
      const file = new File(['content'], 'id.jpg', { type: 'image/jpeg' });
      kycService.requestUploadUrl.mockReturnValue(of({ documentId: 'doc-1', uploadUrl: 'https://minio.local/x' }));
      kycService.uploadFile.mockReturnValue(of(undefined));
      kycService.completeUpload.mockReturnValue(of(identitySubmittedOneDocumentUploaded));

      component.onFileSelected(fileInputEvent(file), 'ID_DOCUMENT');

      expect(kycService.requestUploadUrl).toHaveBeenCalledWith('ID_DOCUMENT');
      expect(kycService.uploadFile).toHaveBeenCalledWith('https://minio.local/x', file);
      expect(kycService.completeUpload).toHaveBeenCalledWith('doc-1');
      expect(component.status()).toEqual(identitySubmittedOneDocumentUploaded);
      expect(component.uploadingType()).toBeNull();
    });

    it('shows the backend error message when requesting the upload URL fails', () => {
      const file = new File(['content'], 'id.jpg', { type: 'image/jpeg' });
      kycService.requestUploadUrl.mockReturnValue(
        throwError(() => ({ error: { message: 'Submit your identity details first' } })),
      );

      component.onFileSelected(fileInputEvent(file), 'ID_DOCUMENT');

      expect(component.error()).toBe('Submit your identity details first');
      expect(component.uploadingType()).toBeNull();
    });

    it('falls back to a generic message when the upload itself fails', () => {
      const file = new File(['content'], 'id.jpg', { type: 'image/jpeg' });
      kycService.requestUploadUrl.mockReturnValue(of({ documentId: 'doc-1', uploadUrl: 'https://minio.local/x' }));
      kycService.uploadFile.mockReturnValue(throwError(() => ({})));

      component.onFileSelected(fileInputEvent(file), 'ID_DOCUMENT');

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
      expect(compiled.querySelector('input[type="file"]')).toBeFalsy();
      const buttons = Array.from(compiled.querySelectorAll('button')).map((b) => b.textContent?.trim());
      expect(buttons.some((text) => text?.includes('Back'))).toBe(true);
    });

    it('shows the document upload section once identity details are submitted', () => {
      kycService.getStatus.mockReturnValue(of(identitySubmittedNoDocuments));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('input[name="firstName"]')).toBeFalsy();
      const fileInputs = compiled.querySelectorAll('input[type="file"]');
      expect(fileInputs.length).toBe(2);
    });

    it('shows "Uploaded" for a document already uploaded and leaves the other pending', () => {
      kycService.getStatus.mockReturnValue(of(identitySubmittedOneDocumentUploaded));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Uploaded');
    });

    it('hides the upload section and shows a Go to Accounts button once verified', () => {
      kycService.getStatus.mockReturnValue(of(verified));
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.querySelector('input[type="file"]')).toBeFalsy();
      expect(compiled.textContent).toContain('Verified');
      const buttons = Array.from(compiled.querySelectorAll('button')).map((b) => b.textContent?.trim());
      expect(buttons.some((text) => text?.includes('Go to Accounts'))).toBe(true);
    });
  });
});
