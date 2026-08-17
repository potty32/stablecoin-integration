import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  TransactionService,
  TransactionResponse
} from '../../../core/services/transaction.service';

@Component({
  selector: 'app-approval-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div>
      <!-- Header -->
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem">
        <div>
          <h1 style="font-size:1.375rem;font-weight:700;margin:0 0 0.25rem 0">Freigabe-Dashboard</h1>
          <p style="margin:0;color:#6b7280;font-size:0.875rem">
            Transfers die auf Ihre Genehmigung warten
          </p>
        </div>
        <button (click)="load()"
                style="border:1px solid #d1d5db;border-radius:6px;padding:0.375rem 0.875rem;font-size:0.875rem;background:white;cursor:pointer;color:#374151">
          ↻ Aktualisieren
        </button>
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

      <!-- Empty state -->
      <div *ngIf="!loading && !error && pending.length === 0"
           style="background:white;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);padding:4rem;text-align:center;color:#94a3b8">
        <div style="font-size:2rem;margin-bottom:0.75rem">✅</div>
        <p style="font-weight:600;color:#374151;margin:0 0 0.25rem 0">Alle Transfers freigegeben</p>
        <p style="margin:0;font-size:0.875rem">Es gibt derzeit keine ausstehenden Freigaben.</p>
      </div>

      <!-- Cards -->
      <div *ngIf="!loading && pending.length > 0"
           style="display:grid;gap:1rem">
        <div *ngFor="let tx of pending"
             style="background:white;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);border-left:4px solid #f59e0b;padding:1.25rem">
          <div style="display:flex;justify-content:space-between;align-items:flex-start">
            <div>
              <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.5rem">
                <span style="font-family:monospace;font-size:0.8rem;color:#64748b;background:#f1f5f9;padding:2px 8px;border-radius:4px">
                  {{ tx.transactionId.substring(0, 8) }}…
                </span>
                <span style="background:#ffedd5;color:#c2410c;padding:2px 8px;border-radius:12px;font-size:0.72rem;font-weight:600">
                  PENDING_APPROVAL
                </span>
              </div>
              <div style="font-size:1.5rem;font-weight:700;margin-bottom:0.25rem">
                {{ tx.amountFiat | number:'1.2-2' }} EUR
              </div>
              <div style="font-size:0.8rem;color:#6b7280">
                {{ tx.currency }} · Erstellt: {{ tx.createdAt | date:'dd.MM.yyyy HH:mm' }}
              </div>
            </div>
            <div style="display:flex;flex-direction:column;gap:0.5rem;align-items:flex-end">
              <button (click)="approve(tx.transactionId)"
                      [disabled]="actioning === tx.transactionId"
                      style="background:#16a34a;color:white;border:none;padding:0.5rem 1.25rem;border-radius:6px;font-size:0.875rem;cursor:pointer;font-weight:500;min-width:120px"
                      [style.opacity]="actioning === tx.transactionId ? '0.6' : '1'">
                {{ actioning === tx.transactionId ? 'Wird verarbeitet…' : '✓ Freigeben' }}
              </button>
              <button (click)="reject(tx.transactionId)"
                      [disabled]="actioning === tx.transactionId"
                      style="background:white;color:#dc2626;border:1px solid #fca5a5;padding:0.5rem 1.25rem;border-radius:6px;font-size:0.875rem;cursor:pointer;font-weight:500;min-width:120px"
                      [style.opacity]="actioning === tx.transactionId ? '0.6' : '1'">
                ✕ Ablehnen
              </button>
            </div>
          </div>
          <div *ngIf="actionErrors[tx.transactionId]"
               style="margin-top:0.75rem;padding:0.625rem 0.875rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;display:flex;align-items:flex-start;gap:0.5rem">
            <span style="color:#dc2626;font-size:1rem;flex-shrink:0">&#9888;</span>
            <span style="color:#991b1b;font-size:0.8rem;line-height:1.4">{{ actionErrors[tx.transactionId] }}</span>
          </div>
        </div>
      </div>

      <!-- Summary footer -->
      <div *ngIf="!loading && pending.length > 0"
           style="margin-top:1rem;font-size:0.8rem;color:#6b7280;text-align:right">
        {{ pending.length }} ausstehende Freigabe(n)
      </div>
    </div>
  `
})
export class ApprovalDashboardComponent implements OnInit {
  private readonly txService = inject(TransactionService);

  pending: TransactionResponse[] = [];
  loading = false;
  error = '';
  actioning = '';
  actionErrors: Record<string, string> = {};

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.actionErrors = {};
    this.txService.listB2bTransfers(0, 50, 'PENDING_APPROVAL').subscribe({
      next: page => {
        this.pending = page.content;
        this.loading = false;
      },
      error: (err: { error?: { message?: string } }) => {
        this.error = err.error?.message ?? 'Fehler beim Laden.';
        this.loading = false;
      }
    });
  }

  approve(transactionId: string): void {
    const approverId = prompt('Approver-ID eingeben:');
    if (!approverId) return;
    this.actioning = transactionId;
    delete this.actionErrors[transactionId];
    this.txService.approveTransfer(transactionId, approverId).subscribe({
      next: () => { this.actioning = ''; this.load(); },
      error: (err: { error?: { errorCode?: string; message?: string; traceId?: string } }) => {
        this.actioning = '';
        this.actionErrors[transactionId] = this.friendlyError(err.error);
      }
    });
  }

  reject(transactionId: string): void {
    const approverId = prompt('Approver-ID für Ablehnung eingeben:');
    if (!approverId) return;
    this.actioning = transactionId;
    delete this.actionErrors[transactionId];
    this.txService.rejectTransfer(transactionId, approverId).subscribe({
      next: () => { this.actioning = ''; this.load(); },
      error: (err: { error?: { errorCode?: string; message?: string; traceId?: string } }) => {
        this.actioning = '';
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
}
