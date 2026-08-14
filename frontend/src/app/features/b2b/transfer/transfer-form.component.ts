import { Component, inject, OnDestroy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Subscription, interval } from 'rxjs';
import { TransactionService, RateQuoteResponse } from '../../../core/services/transaction.service';

@Component({
  selector: 'app-transfer-form',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, RouterLink],
  template: `
    <div style="max-width:640px">
      <div style="display:flex;align-items:center;gap:1rem;margin-bottom:1.5rem">
        <a routerLink="/b2b/transfers" style="color:#64748b;font-size:0.875rem">← Zurück</a>
        <h1 style="font-size:1.375rem;font-weight:700;margin:0">Neue B2B Stablecoin-Überweisung</h1>
      </div>

      <form [formGroup]="form" (ngSubmit)="submit()"
            style="background:white;padding:1.75rem;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);display:flex;flex-direction:column;gap:1.125rem">

        <div>
          <label class="field-label">Quell-IBAN</label>
          <input formControlName="sourceIban" type="text" placeholder="DE89370400440532013000" class="field-input"
                 style="width:100%;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
          @if (form.get('sourceIban')?.invalid && form.get('sourceIban')?.touched) {
            <span style="color:#dc2626;font-size:0.75rem">Pflichtfeld</span>
          }
        </div>

        <div>
          <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">Ziel-Wallet-Adresse</label>
          <input formControlName="destinationWallet" type="text" placeholder="0xABCDEF..."
                 style="width:100%;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.8rem;font-family:monospace">
          @if (form.get('destinationWallet')?.invalid && form.get('destinationWallet')?.touched) {
            <span style="color:#dc2626;font-size:0.75rem">Ungültige Ethereum-Adresse (0x + 40 Hex-Zeichen)</span>
          }
        </div>

        <div style="display:grid;grid-template-columns:1fr 140px;gap:0.75rem">
          <div>
            <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">Betrag (EUR)</label>
            <input formControlName="amountEur" type="number" min="0.01" step="0.01"
                   style="width:100%;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
          </div>
          <div>
            <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">Währung</label>
            <select formControlName="currency"
                    style="width:100%;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem;background:white">
              <option value="USDC">USDC</option>
              <option value="EURC">EURC</option>
            </select>
          </div>
        </div>

        <!-- FX-Kursanfrage (60s-Fenster) -->
        @if (!quote) {
          <button type="button" (click)="fetchQuote()"
                  [disabled]="!form.get('amountEur')?.valid || quoteLoading"
                  style="padding:0.5rem;background:#f0f9ff;border:1px solid #bae6fd;border-radius:6px;color:#0369a1;font-size:0.85rem;font-weight:500;width:100%">
            {{ quoteLoading ? 'Kurs wird abgerufen...' : 'Kurs sichern (60s-Fenster)' }}
          </button>
        } @else {
          <div style="background:#f0fdf4;border:1px solid #86efac;border-radius:6px;padding:0.875rem">
            <div style="display:flex;justify-content:space-between;align-items:flex-start">
              <div>
                <div style="font-size:0.75rem;color:#166534;font-weight:600">KURS GESICHERT</div>
                <div style="font-size:1.1rem;font-weight:700;color:#14532d;margin-top:2px">
                  {{ quote.sourceAmount | number:'1.2-2' }} EUR → {{ quote.targetAmount }} {{ form.get('currency')?.value }}
                </div>
                <div style="font-size:0.75rem;color:#166534;margin-top:2px">
                  Kurs: {{ quote.rate | number:'1.6-6' }} · Spread: {{ quote.spreadPercent | number:'1.2-2' }}% · Gebühr: {{ quote.fee | number:'1.2-2' }} EUR
                </div>
              </div>
              <div [style.color]="quoteSecondsLeft < 15 ? '#dc2626' : '#166534'"
                   style="font-size:1.25rem;font-weight:700;font-variant-numeric:tabular-nums">
                {{ quoteSecondsLeft }}s
              </div>
            </div>
          </div>
        }

        <div>
          <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">Verwendungszweck</label>
          <input formControlName="reference" type="text" placeholder="Rechnungsnr. 2026-042"
                 style="width:100%;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
        </div>

        @if (error) {
          <div style="padding:0.75rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;color:#991b1b;font-size:0.85rem">
            {{ error }}
          </div>
        }

        @if (success) {
          <div style="padding:0.75rem;background:#f0fdf4;border:1px solid #86efac;border-radius:6px;color:#166534;font-size:0.85rem">
            ✓ Transfer erfolgreich initiiert. ID: {{ success }}
          </div>
        }

        <div style="display:flex;gap:0.75rem;justify-content:flex-end;margin-top:0.25rem">
          <a routerLink="/b2b/transfers"
             style="padding:0.5rem 1.25rem;border:1px solid #e2e8f0;border-radius:6px;background:white;color:#374151;font-size:0.875rem;font-weight:500">
            Abbrechen
          </a>
          <button type="submit" [disabled]="form.invalid || submitting"
                  style="padding:0.5rem 1.5rem;background:#2563eb;color:white;border:none;border-radius:6px;font-weight:600;font-size:0.875rem">
            {{ submitting ? 'Wird gesendet…' : 'Transfer initiieren' }}
          </button>
        </div>
      </form>
    </div>
  `
})
export class TransferFormComponent implements OnDestroy {
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private txService = inject(TransactionService);

  submitting = false;
  quoteLoading = false;
  error: string | null = null;
  success: string | null = null;
  quote: RateQuoteResponse | null = null;
  quoteSecondsLeft = 60;
  private countdownSub?: Subscription;

  form = this.fb.group({
    sourceIban: ['DE89370400440532013000', [Validators.required, Validators.minLength(15)]],
    destinationWallet: ['', [Validators.required, Validators.pattern(/^0x[a-fA-F0-9]{40}$/)]],
    amountEur: [null as number | null, [Validators.required, Validators.min(0.01)]],
    currency: ['USDC' as 'USDC' | 'EURC', Validators.required],
    reference: ['']
  });

  fetchQuote(): void {
    const amount = this.form.get('amountEur')?.value;
    const currency = this.form.get('currency')?.value;
    if (!amount || !currency) return;

    this.quoteLoading = true;
    this.txService.getRateQuote(amount, currency).subscribe({
      next: (q) => {
        this.quote = q;
        this.quoteSecondsLeft = 60;
        this.quoteLoading = false;
        this.startCountdown();
      },
      error: () => {
        this.quoteLoading = false;
        this.error = 'Kurs konnte nicht abgerufen werden.';
      }
    });
  }

  private startCountdown(): void {
    this.countdownSub?.unsubscribe();
    this.countdownSub = interval(1000).subscribe(() => {
      this.quoteSecondsLeft--;
      if (this.quoteSecondsLeft <= 0) {
        this.quote = null;
        this.countdownSub?.unsubscribe();
      }
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.error = null;
    this.success = null;

    const idempotencyKey = crypto.randomUUID();
    const { sourceIban, destinationWallet, amountEur, currency, reference } = this.form.value;

    this.txService.initiateB2bTransfer(idempotencyKey, {
      sourceIban: sourceIban!,
      destinationWallet: destinationWallet!,
      amountEur: amountEur!,
      currency: currency!,
      rateQuoteId: this.quote?.quoteId,
      reference: reference ?? undefined
    }).subscribe({
      next: (tx) => {
        this.submitting = false;
        this.success = tx.transactionId;
        this.countdownSub?.unsubscribe();
        setTimeout(() => this.router.navigate(['/b2b/transfers']), 2000);
      },
      error: (err) => {
        this.submitting = false;
        this.error = err.error?.message ?? 'Transfer fehlgeschlagen. Bitte erneut versuchen.';
      }
    });
  }

  ngOnDestroy(): void {
    this.countdownSub?.unsubscribe();
  }
}
