import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TransactionService, RemittanceResponse } from '../../../core/services/transaction.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-remittance-form',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, RouterLink],
  template: `
    <div style="max-width:560px">
      <h1 style="font-size:1.375rem;font-weight:700;margin-bottom:0.375rem">Internationale Sofortüberweisung</h1>
      <p style="color:#64748b;font-size:0.875rem;margin-bottom:1.75rem">
        Überweisung in Echtzeit · Gebühr nur <strong>0,50 EUR</strong>
      </p>

      @if (successData) {
        <div style="background:#f0fdf4;border:1px solid #86efac;border-radius:8px;padding:1.75rem">
          <div style="font-size:2rem;margin-bottom:0.5rem;text-align:center">&#10003;</div>
          <div style="font-weight:700;color:#166534;font-size:1.1rem;text-align:center;margin-bottom:1rem">
            Überweisung erfolgreich aufgegeben!
          </div>
          <table style="width:100%;border-collapse:collapse;font-size:0.875rem">
            <tr>
              <td style="padding:0.35rem 0;color:#374151;font-weight:600">Referenznummer</td>
              <td style="padding:0.35rem 0;color:#166534;font-weight:700">{{ successData.trackingCode }}</td>
            </tr>
            <tr>
              <td style="padding:0.35rem 0;color:#374151;font-weight:600">Empfänger erhält ca.</td>
              <td style="padding:0.35rem 0;color:#166534">{{ successData.recipientReceivesApprox }}</td>
            </tr>
            <tr>
              <td style="padding:0.35rem 0;color:#374151;font-weight:600">Voraussichtliche Gutschrift</td>
              <td style="padding:0.35rem 0;color:#166534">{{ successData.estimatedArrival }}</td>
            </tr>
            <tr>
              <td style="padding:0.35rem 0;color:#374151;font-weight:600">Überweisungsgebühr</td>
              <td style="padding:0.35rem 0;color:#166534">{{ successData.feeEur | number:'1.2-2' }} EUR</td>
            </tr>
          </table>
          <div style="text-align:center;margin-top:1.25rem">
            <a routerLink="." (click)="reset()"
               style="display:inline-block;color:#2563eb;font-weight:500;font-size:0.875rem;cursor:pointer">
              Neue Überweisung aufgeben
            </a>
          </div>
        </div>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()"
              style="background:white;padding:1.75rem;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);display:flex;flex-direction:column;gap:1rem">

          <div>
            <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">
              Ihr Konto (IBAN)
            </label>
            <input formControlName="sourceIban" type="text" placeholder="DE89370400440532013000"
                   style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
          </div>

          <div>
            <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">
              Mobilnummer des Empfängers
            </label>
            <input formControlName="recipientPhone" type="tel" placeholder="+521 55 5123 4567"
                   style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
            @if (form.get('recipientPhone')?.invalid && form.get('recipientPhone')?.touched) {
              <span style="color:#dc2626;font-size:0.75rem">
                Format: +[Ländervorwahl][Nummer], z.&nbsp;B. +4915112345678
              </span>
            }
          </div>

          <div>
            <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">
              Betrag (EUR)
            </label>
            <input formControlName="amountEur" type="number" min="1" step="0.01"
                   style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
          </div>

          <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.75rem">
            <div>
              <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">
                Zielland
              </label>
              <select formControlName="recipientCountry"
                      style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem;background:white">
                <option value="">Bitte wählen</option>
                <option value="MX">Mexiko</option>
                <option value="PH">Philippinen</option>
                <option value="IN">Indien</option>
                <option value="NG">Nigeria</option>
              </select>
            </div>
            <div>
              <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">
                Name des Empfängers
              </label>
              <input formControlName="recipientName" type="text" placeholder="Maria Garcia"
                     style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
            </div>
          </div>

          <div style="background:#f0f9ff;border:1px solid #bae6fd;border-radius:6px;padding:0.875rem;font-size:0.85rem;color:#0369a1">
            <span style="font-size:1.1rem;font-weight:700;color:#0c4a6e">Gebühr: 0,50 EUR</span>
            &nbsp;&nbsp;·&nbsp;&nbsp;Gutschrift beim Empfänger in der Regel innerhalb von Sekunden
          </div>

          @if (error) {
            <div style="padding:0.75rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;color:#991b1b;font-size:0.85rem">
              {{ error }}
            </div>
          }

          <button type="submit" [disabled]="form.invalid || submitting"
                  style="width:100%;padding:0.75rem;background:#2563eb;color:white;border:none;border-radius:6px;font-weight:600;font-size:0.95rem;margin-top:0.25rem;cursor:pointer">
            {{ submitting ? 'Wird gesendet…' : 'Jetzt senden' }}
          </button>
        </form>
      }
    </div>
  `
})
export class RemittanceFormComponent {
  private fb = inject(FormBuilder);
  private txService = inject(TransactionService);
  private auth = inject(AuthService);

  submitting = false;
  error: string | null = null;
  successData: RemittanceResponse | null = null;

  form = this.fb.group({
    sourceIban: [this.auth.getIban(), Validators.required],
    recipientPhone: ['', [Validators.required, Validators.pattern(/^\+[1-9]\d{6,14}$/)]],
    amountEur: [null as number | null, [Validators.required, Validators.min(1)]],
    recipientCountry: ['', Validators.required],
    recipientName: ['', Validators.required]
  });

  submit(): void {
    if (this.form.invalid) return;
    this.submitting = true;
    this.error = null;

    const idempotencyKey = crypto.randomUUID();
    const v = this.form.value;

    this.txService.sendRemittance(idempotencyKey, {
      sourceIban: v.sourceIban!,
      recipientPhone: v.recipientPhone!,
      amountEur: v.amountEur!,
      recipientCountry: v.recipientCountry!,
      recipientName: v.recipientName!
    }).subscribe({
      next: (response) => {
        this.submitting = false;
        this.successData = response;
      },
      error: (err: { status: number; error?: { message?: string } }) => {
        this.submitting = false;
        this.error = err.error?.message ?? 'Die Überweisung konnte nicht durchgeführt werden. Bitte versuchen Sie es erneut.';
      }
    });
  }

  reset(): void {
    this.successData = null;
    this.form.reset();
  }
}
