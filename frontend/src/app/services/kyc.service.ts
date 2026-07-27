import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  KycDocumentType,
  KycStatusResponse,
  SubmitIdentityRequest,
  UploadUrlResponse,
} from '../models/bank.models';

@Injectable({ providedIn: 'root' })
export class KycService {
  private readonly api = '/api/kyc';

  constructor(private http: HttpClient) {}

  getStatus(): Observable<KycStatusResponse> {
    return this.http.get<KycStatusResponse>(`${this.api}/status`);
  }

  submitIdentity(req: SubmitIdentityRequest): Observable<KycStatusResponse> {
    return this.http.post<KycStatusResponse>(`${this.api}/identity`, req);
  }

  requestUploadUrl(type: KycDocumentType): Observable<UploadUrlResponse> {
    return this.http.post<UploadUrlResponse>(`${this.api}/documents/upload-url`, { type });
  }

  // Uploads straight to the presigned URL (S3/MinIO), not through our own API - the file bytes
  // never pass through identity-service. See jwt.interceptor.ts for why this is safe to call with
  // HttpClient without also picking up our own Authorization header.
  uploadFile(uploadUrl: string, file: File): Observable<void> {
    return this.http.put(uploadUrl, file).pipe(map(() => undefined));
  }

  completeUpload(documentId: string): Observable<KycStatusResponse> {
    return this.http.post<KycStatusResponse>(`${this.api}/documents/${documentId}/complete`, {});
  }
}
