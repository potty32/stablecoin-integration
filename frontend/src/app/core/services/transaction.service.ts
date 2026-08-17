import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface TransferPageResponse {
  content: TransactionResponse[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface AddressBookEntry {
  id: string;
  label: string;
  walletAddress: string;
  currency: string;
  riskScore: string;
  status: string;
  verifiedAt: string;
}

export interface TransactionResponse {
  transactionId: string;
  type: 'OUTBOUND' | 'INBOUND' | 'BULK' | 'P2P' | 'REMITTANCE' | 'YIELD_DEPOSIT' | 'YIELD_REDEEM';
  status: 'CREATED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXPIRED' |
          'COMPLIANCE_CHECKED' | 'FUNDS_HELD' | 'SUBMITTED' | 'SETTLED' | 'REDEEMED' | 'FAILED';
  amountFiat: number;
  amountStablecoin: number;
  currency: 'USDC' | 'EURC';
  blockchainHash?: string;
  grossRevenue?: number;
  requiresApproval: boolean;
  createdAt: string;
  settledAt?: string;
  timeline: Array<{ status: string; at: string }>;
}

export interface InitiateTransferRequest {
  sourceIban: string;
  destinationWallet: string;
  amountEur: number;
  currency: 'USDC' | 'EURC';
  rateQuoteId?: string;
  purposeCode?: string;
  reference?: string;
}

export interface RateQuoteResponse {
  quoteId: string;
  sourceAmount: number;
  targetAmount: string;
  rate: number;
  spreadPercent: number;
  fee: number;
  validUntil: string;
  lockedForSeconds: number;
}

export interface RemittanceRequest {
  sourceIban: string;
  recipientPhone: string;
  amountEur: number;
  recipientCountry: string;
  recipientName: string;
}

export interface RemittanceResponse {
  transactionId: string;
  status: string;
  feeEur: number;
  recipientReceivesApprox: string;
  estimatedArrival: string;
  trackingCode: string;
}

export interface YieldPositionResponse {
  positionId: string;    // YieldPosition.id (war: depositId = TX.id)
  amountEur: number;
  depositDate: string;
  currentValueEur: number;
  dailyYieldEur: number;
  yieldRatePercent: number;
  status: string;        // YieldStatus: 'ACTIVE' | 'CLOSED'
}

export interface AccountBalanceResponse {
  iban: string;
  balanceEur: number;
  stablecoinBalances: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private http = inject(HttpClient);
  private base = environment.apiBaseUrl;

  initiateB2bTransfer(idempotencyKey: string, request: InitiateTransferRequest): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(`${this.base}/b2b/transfers`, request, {
      headers: { 'X-Idempotency-Key': idempotencyKey }
    });
  }

  getTransaction(id: string): Observable<TransactionResponse> {
    return this.http.get<TransactionResponse>(`${this.base}/transactions/${id}`);
  }

  approveTransfer(id: string, approverId: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(`${this.base}/b2b/transfers/${id}/approve`, { approverId });
  }

  rejectTransfer(id: string, approverId: string): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(`${this.base}/b2b/transfers/${id}/reject`, { approverId });
  }

  getRateQuote(amountEur: number, currency: 'USDC' | 'EURC'): Observable<RateQuoteResponse> {
    return this.http.get<RateQuoteResponse>(`${this.base}/b2b/rate-quote`, {
      params: { amountEur, currency }
    });
  }

  sendRemittance(idempotencyKey: string, request: RemittanceRequest): Observable<RemittanceResponse> {
    return this.http.post<RemittanceResponse>(`${this.base}/b2c/remittances`, request, {
      headers: { 'X-Idempotency-Key': idempotencyKey }
    });
  }

  registerPhoneAlias(sourceIban: string, phoneNumber: string, walletAddress: string): Observable<void> {
    return this.http.post<void>(`${this.base}/b2c/p2p/phone/register`, {
      sourceIban, phoneNumber, walletAddress
    });
  }

  sendP2pPhone(idempotencyKey: string, sourceIban: string, recipientPhone: string, amountEur: number): Observable<TransactionResponse> {
    return this.http.post<TransactionResponse>(`${this.base}/b2c/p2p/phone`, {
      sourceIban, recipientPhone, amountEur
    }, {
      headers: { 'X-Idempotency-Key': idempotencyKey }
    });
  }

  getYieldPosition(): Observable<YieldPositionResponse> {
    return this.http.get<YieldPositionResponse>(`${this.base}/b2c/savings/yield`);
  }

  depositYield(idempotencyKey: string, sourceIban: string, amountEur: number): Observable<YieldPositionResponse> {
    return this.http.post<YieldPositionResponse>(`${this.base}/b2c/savings/yield/deposit`,
      { sourceIban, amountEur },
      { headers: { 'X-Idempotency-Key': idempotencyKey } }
    );
  }

  redeemYield(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/b2c/savings/yield/${id}`);
  }

  getCardWallet(): Observable<{ walletAddress: string; balanceUsdc: string; balanceEurc: string }> {
    return this.http.get<{ walletAddress: string; balanceUsdc: string; balanceEurc: string }>(`${this.base}/b2c/card/wallet`);
  }

  getAccountBalance(iban: string): Observable<AccountBalanceResponse> {
    return this.http.get<AccountBalanceResponse>(`${this.base}/accounts/${iban}/balance`);
  }

  listB2bTransfers(page = 0, size = 20, status?: string): Observable<TransferPageResponse> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<TransferPageResponse>(`${this.base}/b2b/transfers`, { params });
  }

  listAddressBook(): Observable<AddressBookEntry[]> {
    return this.http.get<AddressBookEntry[]>(`${this.base}/b2b/address-book`);
  }

  addAddress(label: string, walletAddress: string, currency: string): Observable<AddressBookEntry> {
    return this.http.post<AddressBookEntry>(`${this.base}/b2b/address-book`, { label, walletAddress, currency });
  }

  revokeAddress(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/b2b/address-book/${id}`);
  }
}
