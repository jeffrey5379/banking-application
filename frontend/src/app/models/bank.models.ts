export type Currency = "EUR" | "USD" | "CHF" | "GBP" | "SEK" | "PLN";
export type TransactionType =
  | "CREDIT"
  | "DEBIT"
  | "EXCHANGE_IN"
  | "EXCHANGE_OUT"
  | "TRANSFER_IN"
  | "TRANSFER_OUT";

export interface User {
  id: string;
  username: string;
  email: string;
  accounts?: Account[];
}

export interface Account {
  id: string;
  accountNumber: string;
  currency: Currency;
  balance: number;
  userId: string;
  username: string;
}

export interface AccountDetail extends Account {
  transactions: Transaction[];
}

export interface Transaction {
  id: string;
  accountId: string;
  accountNumber: string;
  type: TransactionType;
  amount: number;
  currency: Currency;
  balanceAfter: number;
  description: string;
  createdAt: string;
  exchangeRate?: number;
  relatedAccountId?: string;
  relatedAccountNumber?: string;
}

export interface ExchangeRequest {
  amount: number;
  targetAccountId: string;
}

export interface TransferRequest {
  amount: number;
  targetUsername: string;
  targetAccountNumber: string;
  description?: string;
}

export interface RecipientCheckResponse {
  valid: boolean;
}

export interface CreateAccountRequest {
  currency: Currency;
}

export interface BalancePoint {
  balance: number;
  createdAt: string;
}

export interface TransactionPage {
  content: Transaction[];
  page: number;
  size: number;
  totalElements: number;
  last: boolean;
}

export interface LoginChallengeResponse {
  challengeToken: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  username: string;
}

export interface AccountStats {
  totalIn: number;
  totalOut: number;
}

export type KycStatus = "NOT_STARTED" | "PENDING" | "VERIFIED" | "REJECTED";
export type KycLevel = "NONE" | "BASIC" | "ENHANCED";

export type IssuingCountry =
  | "GERMANY"
  | "FRANCE"
  | "ITALY"
  | "SPAIN"
  | "NETHERLANDS"
  | "PORTUGAL"
  | "POLAND"
  | "UNITED_KINGDOM"
  | "SWITZERLAND"
  | "SWEDEN"
  | "UNITED_STATES"
  | "CANADA"
  | "AUSTRALIA"
  | "JAPAN";

export type KycDocumentType = "ID_DOCUMENT" | "SELFIE";

export interface KycDocumentSummary {
  id: string;
  type: KycDocumentType;
  uploaded: boolean;
  uploadedAt?: string;
}

export interface KycStatusResponse {
  userId: string;
  status: KycStatus;
  level: KycLevel;
  firstName?: string;
  lastName?: string;
  issuingCountry?: IssuingCountry;
  documentNumber?: string;
  submittedAt?: string;
  reviewedAt?: string;
  rejectionReason?: string;
  documents: KycDocumentSummary[];
}

export interface SubmitIdentityRequest {
  firstName: string;
  lastName: string;
  issuingCountry: IssuingCountry;
  documentNumber: string;
}

export interface UploadUrlRequest {
  type: KycDocumentType;
}

export interface UploadUrlResponse {
  documentId: string;
  uploadUrl: string;
}

export type MessagePriority = "NORMAL" | "HIGH";

export interface Message {
  id: string;
  subject: string;
  body: string;
  receivedAt: string;
  read: boolean;
  priority: MessagePriority;
}
