import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TransactionService, TransactionResponse } from '../../../core/services/transaction.service';
import { AuthService } from '../../../core/services/auth.service';

type Tab = 'send' | 'register';

@Component({
  selector: 'app-p2p-phone',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule],
  template: `
    <div style="max-width:520px">
      <h1 style="font-size:1.375rem;font-weight:700;margin-bottom:0.375rem">Telefonüberweisung</h1>
      <p style="color:#64748b;font-size:0.875rem;margin-bottom:1.5rem">
        Überweisen Sie Geld direkt an eine Mobilnummer.
      </p>

      <!-- Tabs -->
      <div style="display:flex;border-bottom:2px solid #e2e8f0;margin-bottom:1.5rem">
        <button (click)="activeTab = 'send'"
                style="padding:0.625rem 1.25rem;border:none;background:none;font-size:0.875rem;font-weight:600;cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-2px;transition:color 0.15s"
                [style.color]="activeTab === 'send' ? '#2563eb' : '#64748b'"
                [style.border-bottom-color]="activeTab === 'send' ? '#2563eb' : 'transparent'">
          Geld senden
        </button>
        <button (click)="activeTab = 'register'"
                style="padding:0.625rem 1.25rem;border:none;background:none;font-size:0.875rem;font-weight:600;cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-2px;transition:color 0.15s"
                [style.color]="activeTab === 'register' ? '#2563eb' : '#64748b'"
                [style.border-bottom-color]="activeTab === 'register' ? '#2563eb' : 'transparent'">
          Telefon registrieren
        </button>
      </div>

      <!-- Tab: Geld senden -->
      @if (activeTab === 'send') {
        @if (sendSuccess) {
          <div style="background:#f0fdf4;border:1px solid #86efac;border-radius:8px;padding:1.5rem;text-align:center">
            <div style="font-size:2rem;margin-bottom:0.5rem">&#10003;</div>
            <div style="font-weight:700;color:#166534;font-size:1rem;margin-bottom:0.5rem">
              Überweisung erfolgreich!
            </div>
            <div style="color:#166534;font-size:0.875rem">
              Vorgang-Nr.: <strong>{{ sendSuccess.transactionId }}</strong>
            </div>
            <button (click)="resetSend()"
                    style="display:inline-block;margin-top:1rem;color:#2563eb;font-weight:500;font-size:0.875rem;background:none;border:none;cursor:pointer">
              Neue Überweisung
            </button>
          </div>
        } @else {
          <form [formGroup]="sendForm" (ngSubmit)="submitSend()"
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
              <input formControlName="recipientPhone" type="tel" placeholder="+4915112345678"
                     style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
              @if (sendForm.get('recipientPhone')?.invalid && sendForm.get('recipientPhone')?.touched) {
                <span style="color:#dc2626;font-size:0.75rem">
                  Format: +[Ländervorwahl][Nummer], z.&nbsp;B. +4915112345678
                </span>
              }
            </div>

            <div>
              <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">
                Betrag (EUR)
              </label>
              <input formControlName="amountEur" type="number" min="0.01" step="0.01"
                     style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
            </div>

            @if (sendError) {
              <div style="padding:0.75rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;color:#991b1b;font-size:0.85rem">
                {{ sendError }}
              </div>
            }

            <button type="submit" [disabled]="sendForm.invalid || sendSubmitting"
                    style="width:100%;padding:0.75rem;background:#2563eb;color:white;border:none;border-radius:6px;font-weight:600;font-size:0.95rem;cursor:pointer">
              {{ sendSubmitting ? 'Wird gesendet…' : 'Jetzt überweisen' }}
            </button>
          </form>
        }
      }

      <!-- Tab: Telefon registrieren -->
      @if (activeTab === 'register') {
        @if (registerSuccess) {
          <div style="background:#f0fdf4;border:1px solid #86efac;border-radius:8px;padding:1.5rem;text-align:center">
            <div style="font-size:2rem;margin-bottom:0.5rem">&#10003;</div>
            <div style="font-weight:700;color:#166534;font-size:1rem">
              Mobilnummer erfolgreich registriert!
            </div>
            <p style="color:#166534;font-size:0.875rem;margin-top:0.5rem">
              Andere Kunden können jetzt Überweisungen an Ihre Nummer senden.
            </p>
            <button (click)="resetRegister()"
                    style="display:inline-block;margin-top:1rem;color:#2563eb;font-weight:500;font-size:0.875rem;background:none;border:none;cursor:pointer">
              Weitere Nummer registrieren
            </button>
          </div>
        } @else {
          <form [formGroup]="registerForm" (ngSubmit)="submitRegister()"
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
                Zu registrierende Mobilnummer
              </label>
              <input formControlName="phoneNumber" type="tel" placeholder="+4915112345678"
                     style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
              @if (registerForm.get('phoneNumber')?.invalid && registerForm.get('phoneNumber')?.touched) {
                <span style="color:#dc2626;font-size:0.75rem">
                  Format: +[Ländervorwahl][Nummer], z.&nbsp;B. +4915112345678
                </span>
              }
            </div>

            <!-- Technisches Feld – für nicht-technische Nutzer zurückgezogen -->
            <details style="border:1px solid #e2e8f0;border-radius:6px;padding:0.75rem">
              <summary style="font-size:0.8rem;font-weight:600;color:#64748b;cursor:pointer">
                Erweiterte Einstellungen
              </summary>
              <div style="margin-top:0.75rem">
                <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">
                  Empfangskennung
                </label>
                <input formControlName="walletAddress" type="text" placeholder="0x…"
                       style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem;font-family:monospace">
                <p style="font-size:0.75rem;color:#64748b;margin:0.375rem 0 0">
                  Technische Kennung für den Zahlungsempfang. Wird von Ihrer Bank vorausgefüllt.
                </p>
              </div>
            </details>

            @if (registerError) {
              <div style="padding:0.75rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;color:#991b1b;font-size:0.85rem">
                {{ registerError }}
              </div>
            }

            <button type="submit" [disabled]="registerForm.invalid || registerSubmitting"
                    style="width:100%;padding:0.75rem;background:#2563eb;color:white;border:none;border-radius:6px;font-weight:600;font-size:0.95rem;cursor:pointer">
              {{ registerSubmitting ? 'Wird registriert…' : 'Mobilnummer registrieren' }}
            </button>
          </form>
        }
      }
    </div>
  `
})
export class P2pPhoneComponent {
  private fb = inject(FormBuilder);
  private txService = inject(TransactionService);
  private auth = inject(AuthService);

  activeTab: Tab = 'send';

  // --- Send tab state ---
  sendSubmitting = false;
  sendError: string | null = null;
  sendSuccess: TransactionResponse | null = null;

  sendForm = this.fb.group({
    sourceIban: [this.auth.getIban(), Validators.required],
    recipientPhone: ['', [Validators.required, Validators.pattern(/^\+[1-9]\d{6,14}$/)]],
    amountEur: [null as number | null, [Validators.required, Validators.min(0.01)]]
  });

  submitSend(): void {
    if (this.sendForm.invalid) return;
    this.sendSubmitting = true;
    this.sendError = null;

    const idempotencyKey = crypto.randomUUID();
    const v = this.sendForm.value;

    this.txService.sendP2pPhone(
      idempotencyKey,
      v.sourceIban!,
      v.recipientPhone!,
      v.amountEur!
    ).subscribe({
      next: (response) => {
        this.sendSubmitting = false;
        this.sendSuccess = response;
      },
      error: (err: { error?: { message?: string } }) => {
        this.sendSubmitting = false;
        this.sendError = err.error?.message ?? 'Die Überweisung konnte nicht durchgeführt werden. Bitte versuchen Sie es erneut.';
      }
    });
  }

  resetSend(): void {
    this.sendSuccess = null;
    this.sendForm.reset();
  }

  // --- Register tab state ---
  registerSubmitting = false;
  registerError: string | null = null;
  registerSuccess = false;

  registerForm = this.fb.group({
    sourceIban: [this.auth.getIban(), Validators.required],
    phoneNumber: ['', [Validators.required, Validators.pattern(/^\+[1-9]\d{6,14}$/)]],
    walletAddress: ['', Validators.required]
  });

  submitRegister(): void {
    if (this.registerForm.invalid) return;
    this.registerSubmitting = true;
    this.registerError = null;

    const v = this.registerForm.value;

    this.txService.registerPhoneAlias(
      v.sourceIban!,
      v.phoneNumber!,
      v.walletAddress!
    ).subscribe({
      next: () => {
        this.registerSubmitting = false;
        this.registerSuccess = true;
      },
      error: (err: { error?: { message?: string } }) => {
        this.registerSubmitting = false;
        this.registerError = err.error?.message ?? 'Registrierung fehlgeschlagen. Bitte versuchen Sie es erneut.';
      }
    });
  }

  resetRegister(): void {
    this.registerSuccess = false;
    this.registerForm.reset();
  }
}
