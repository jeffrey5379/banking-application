import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { KycService } from './kyc.service';

describe('KycService', () => {
  let service: KycService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [KycService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(KycService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getStatus → GET /api/kyc/status', () => {
    service.getStatus().subscribe();
    httpMock.expectOne('/api/kyc/status').flush({});
  });

  it('submitIdentity → POST /api/kyc/identity with the form fields', () => {
    service
      .submitIdentity({
        firstName: 'Alice',
        lastName: 'Anderson',
        issuingCountry: 'UNITED_KINGDOM',
        documentNumber: 'AA1234567',
      })
      .subscribe();

    const req = httpMock.expectOne('/api/kyc/identity');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      firstName: 'Alice',
      lastName: 'Anderson',
      issuingCountry: 'UNITED_KINGDOM',
      documentNumber: 'AA1234567',
    });
    req.flush({});
  });

  it('requestUploadUrl → POST /api/kyc/documents/upload-url with the document type', () => {
    service.requestUploadUrl('SELFIE').subscribe();

    const req = httpMock.expectOne('/api/kyc/documents/upload-url');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ type: 'SELFIE' });
    req.flush({ documentId: 'doc-1', uploadUrl: 'https://minio.local/x' });
  });

  it('completeUpload → POST /api/kyc/documents/:id/complete', () => {
    service.completeUpload('doc-1').subscribe();

    const req = httpMock.expectOne('/api/kyc/documents/doc-1/complete');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('uploadFile → PUTs the raw file to the given URL, not through /api', () => {
    const file = new File(['content'], 'selfie.jpg', { type: 'image/jpeg' });

    service.uploadFile('https://minio.local/kyc-documents/some-key', file).subscribe();

    const req = httpMock.expectOne('https://minio.local/kyc-documents/some-key');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toBe(file);
    req.flush(null);
  });
});
