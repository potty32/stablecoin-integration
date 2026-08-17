import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  TransactionService,
  TransactionResponse,
  TransferPageResponse,
  AccountBalanceResponse
} from '../../../core/services/transaction.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-transfer-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, KeyValuePipe],
  template: `
    <div>
      <!-- Header -->
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem">
        <h1 style="font-size:1.375rem;font-weight:700;margin:0">B2B Transfers</h1>
        <a routerLink="/b2b/transfers/new"
           style="background:#2563eb;color:white;padding:0.5rem 1.25rem;border-radius:6px;font-weight:500;font-size:0.875rem;text-decoration:none">
          + Neue Überweisung
        </a>
      </div>

      <!-- Balance Widget -->
      @if (balance) {
        <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:0.875rem 1rem;margin-bottom:1rem;display:flex;align-items:center;gap:2rem">
          <div>
            <div style="font-size:0.7rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;margin-bottom:0.2rem">Verfügbares Guthaben</div>
            <div style="font-size:1.25rem;font-weight:700;color:#1e293b">{{ balance.balanceEur | number:'1.2-2' }} EUR</div>
          </div>
          @for (entry of balance.stablecoinBalances | keyvalue; track entry.key) {
            <div>
              <div style="font-size:0.7rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;margin-bottom:0.2rem">{{ entry.key }}</div>
              <div style="font-size:1.25rem;font-weight:700;color:#2563eb">{{ entry.value }}</div>
            </div>
          }
          <div style="font-size:0.75rem;color:#94a3b8;font-family:monospace;margin-left:auto">{{ balance.iban }}</div>
        </div>
      }

      <!-- Filter row -->
      <div style="display:flex;gap:1rem;margin-bottom:1rem;align-items:center">
        <label style="font-size:0.875rem;font-weight:500;color:#374151">Status:</label>
        <select [(ngModel)]="selectedStatus" (ngModelChange)="onStatusChange($event)"
                style="border:1px solid #d1d5db;border-radius:6px;padding:0.375rem 0.75rem;font-size:0.875rem;background:white">
          <option value="">Alle</option>
          <option value="PENDING">PENDING</option>
          <option value="AWAITING_APPROVAL">AWAITING_APPROVAL</option>
          <option value="SETTLED">SETTLED</option>
          <option value="FAILED">FAILED</option>
        </select>
        <span style="font-size:0.8rem;color:#6b7280" *ngIf="!loading">
          {{ totalElements }} Einträge gesamt
        </span>
      </div>

      <!-- Loading -->
      <div *ngIf="loading" style="text-align:center;padding:3rem;color:#94a3b8">
        Wird geladen…
      </div>

      <!-- Error -->
      <div *ngIf="error && !loading"
           style="background:#fee2e2;border:1px solid #fca5a5;border-radius:8px;padding:1rem;margin-bottom:1rem;color:#b91c1c;font-size:0.875rem">
        {{ error }}
      </div>

      <!-- Table -->
      <div *ngIf="!loading" style="background:white;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);overflow:hidden">
        <table style="width:100%;border-collapse:collapse">
          <thead>
            <tr style="background:#f8fafc;border-bottom:1px solid #e2e8f0">
              <th style="padding:0.75rem 1rem;text-align:left;font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em">ID</th>
              <th style="padding:0.75rem 1rem;text-align:left;font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em">Betrag</th>
              <th style="padding:0.75rem 1rem;text-align:left;font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em">Währung</th>
              <th style="padding:0.75rem 1rem;text-align:left;font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em">Status</th>
              <th style="padding:0.75rem 1rem;text-align:left;font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em">Ertrag</th>
              <th style="padding:0.75rem 1rem;text-align:left;font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em">Erstellt</th>
              <th style="padding:0.75rem 1rem;text-align:left;font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em">Aktionen</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let tx of transactions"
                style="border-top:1px solid #f1f5f9;transition:background 0.1s"
                (mouseenter)="hovered=tx.transactionId"
                (mouseleave)="hovered=''"
                [style.background]="hovered === tx.transactionId ? '#f8fafc' : 'white'">
              <td style="padding:0.75rem 1rem;font-family:monospace;font-size:0.8rem;color:#475569">
                {{ tx.transactionId.substring(0, 8) }}…
              </td>
              <td style="padding:0.75rem 1rem;font-weight:600">
                {{ tx.amountFiat | number:'1.2-2' }} EUR
              </td>
              <td style="padding:0.75rem 1rem;color:#475569">{{ tx.currency }}</td>
              <td style="padding:0.75rem 1rem">
                <span [style]="statusStyle(tx.status)">{{ tx.status }}</span>
                <span *ngIf="tx.requiresApproval && tx.status === 'AWAITING_APPROVAL'"
                      style="margin-left:6px;font-size:0.7rem;color:#d97706">⏳ Freigabe</span>
              </td>
              <td style="padding:0.75rem 1rem;color:#059669;font-weight:500">
                {{ tx.grossRevenue ? (tx.grossRevenue | number:'1.2-2') + ' EUR' : '—' }}
              </td>
              <td style="padding:0.75rem 1rem;color:#64748b;font-size:0.8rem">
                {{ tx.createdAt | date:'dd.MM.yyyy HH:mm' }}
              </td>
              <td style="padding:0.75rem 1rem">
                <ng-container *ngIf="tx.status === 'AWAITING_APPROVAL'">
                  <button (click)="approve(tx.transactionId)"
                          style="background:#16a34a;color:white;border:none;padding:0.3rem 0.75rem;border-radius:4px;font-size:0.8rem;cursor:pointer;margin-right:0.5rem;font-weight:500">
                    ✓ Freigeben
                  </button>
                  <button (click)="reject(tx.transactionId)"
                          style="background:#dc2626;color:white;border:none;padding:0.3rem 0.75rem;border-radius:4px;font-size:0.8rem;cursor:pointer;font-weight:500">
                    ✕ Ablehnen
                  </button>
                </ng-container>
                <div *ngIf="actionErrors[tx.transactionId]"
                     style="margin-top:0.5rem;display:flex;align-items:flex-start;gap:0.375rem;padding:0.4rem 0.625rem;background:#fef2f2;border:1px solid #fecaca;border-radius:4px;max-width:260px">
                  <span style="color:#dc2626;flex-shrink:0">&#9888;</span>
                  <span style="color:#991b1b;font-size:0.75rem;line-height:1.4">{{ actionErrors[tx.transactionId] }}</span>
                </div>
              </td>
            </tr>
            <tr *ngIf="transactions.length === 0">
              <td colspan="7" style="padding:3rem;text-align:center;color:#94a3b8">
                Keine Transfers gefunden.
                <a routerLink="/b2b/transfers/new"
                   style="color:#2563eb;font-weight:500;margin-top:0.5rem;display:inline-block">
                  Erste Überweisung erstellen →
                </a>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div *ngIf="!loading && totalPages > 1"
           style="display:flex;justify-content:space-between;align-items:center;margin-top:1rem;padding:0.5rem 0">
        <button (click)="prevPage()" [disabled]="currentPage === 0"
                style="border:1px solid #d1d5db;border-radius:6px;padding:0.375rem 1rem;font-size:0.875rem;background:white;cursor:pointer;color:#374151"
                [style.opacity]="currentPage === 0 ? '0.4' : '1'">
          ← Zurück
        </button>
        <span style="font-size:0.875rem;color:#6b7280">
          Seite {{ currentPage + 1 }} von {{ totalPages }}
        </span>
        <button (click)="nextPage()" [disabled]="currentPage >= totalPages - 1"
                style="border:1px solid #d1d5db;border-radius:6px;padding:0.375rem 1rem;font-size:0.875rem;background:white;cursor:pointer;color:#374151"
                [style.opacity]="currentPage >= totalPages - 1 ? '0.4' : '1'">
          Weiter →
        </button>
      </div>
    </div>
  `
})
export class TransferListComponent implements OnInit {
  private readonly txService = inject(TransactionService);
  private readonly auth     = inject(AuthService);

  balance: AccountBalanceResponse | null = null;
  transactions: TransactionResponse[] = [];
  loading = false;
  error = '';
  hovered = '';
  actionErrors: Record<string, string> = {};

  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;
  selectedStatus = '';

  ngOnInit(): void {
    const iban = this.auth.getIban();
    if (iban) {
      this.txService.getAccountBalance(iban).subscribe({
        next: b  => this.balance = b,
        error: () => {}
      });
    }
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    const status = this.selectedStatus || undefined;
    this.txService.listB2bTransfers(this.currentPage, this.pageSize, status).subscribe({
      next: (page: TransferPageResponse) => {
        this.transactions = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = page.totalPages;
        this.loading = false;
      },
      error: (err: { message?: string }) => {
        this.error = err.message ?? 'Fehler beim Laden der Transfers.';
        this.loading = false;
      }
    });
  }

  onStatusChange(_status: string): void {
    this.currentPage = 0;
    this.load();
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.load();
    }
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.load();
    }
  }

  approve(transactionId: string): void {
    const approverId = prompt('Approver-ID eingeben:');
    if (!approverId) return;
    delete this.actionErrors[transactionId];
    this.txService.approveTransfer(transactionId, approverId).subscribe({
      next: () => this.load(),
      error: (err: { error?: { errorCode?: string; message?: string; traceId?: string } }) => {
        this.actionErrors[transactionId] = this.friendlyError(err.error);
      }
    });
  }

  reject(transactionId: string): void {
    const approverId = prompt('Approver-ID für Ablehnung eingeben:');
    if (!approverId) return;
    delete this.actionErrors[transactionId];
    this.txService.rejectTransfer(transactionId, approverId).subscribe({
      next: () => this.load(),
      error: (err: { error?: { errorCode?: string; message?: string; traceId?: string } }) => {
        this.actionErrors[transactionId] = this.friendlyError(err.error);
      }
    });
  }

  private friendlyError(body?: { errorCode?: string; message?: string; traceId?: string }): string {
    const map: Record<string, string> = {
      TAURUS_001: 'Betrag überschreitet das Custody-Einzellimit von 1.000.000 EUR. Transfer muss aufgeteilt werden.',
      COMPLIANCE_001: 'Freigabe durch Compliance-Prüfung blockiert.',
      SYS_001: 'Systemfehler – bitte Support kontaktieren.',
    };
    const text = body?.errorCode ? (map[body.errorCode] ?? body.message) : (body?.message ?? 'Unbekannter Fehler');
    return body?.traceId ? `${text} (Trace: ${body.traceId.substring(0, 8)})` : (text ?? 'Unbekannter Fehler');
  }

  statusStyle(status: string): string {
    const base = 'display:inline-block;padding:2px 8px;border-radius:12px;font-size:0.72rem;font-weight:600;';
    const map: Record<string, string> = {
      SETTLED: base + 'background:#dcfce7;color:#166534',
      PENDING: base + 'background:#fef9c3;color:#854d0e',
      PROCESSING: base + 'background:#dbeafe;color:#1d4ed8',
      COMPLIANCE_CHECK: base + 'background:#ede9fe;color:#6d28d9',
      AWAITING_APPROVAL: base + 'background:#ffedd5;color:#c2410c',
      FAILED: base + 'background:#fee2e2;color:#b91c1c',
      BLOCKED: base + 'background:#fce7f3;color:#9d174d',
    };
    return map[status] ?? base + 'background:#f1f5f9;color:#475569';
  }
}
