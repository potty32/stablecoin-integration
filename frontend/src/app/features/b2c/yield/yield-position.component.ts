import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TransactionService, YieldPositionResponse } from '../../../core/services/transaction.service';
import { AuthService } from '../../../core/services/auth.service';

type LoadState = 'loading' | 'loaded' | 'error';

@Component({
  selector: 'app-yield-position',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="max-width:560px">
      <h1 style="font-size:1.375rem;font-weight:700;margin-bottom:0.375rem">Sparkonto</h1>
      <p style="color:#64748b;font-size:0.875rem;margin-bottom:1.75rem">
        Tagesgeldähnliches Sparkonto – Ihre Einlage arbeitet täglich für Sie.
      </p>

      @if (loadState === 'loading') {
        <div style="padding:2rem;text-align:center;color:#64748b;font-size:0.875rem">
          Kontodaten werden geladen…
        </div>
      }

      @if (loadState === 'error') {
        <div style="padding:0.75rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;color:#991b1b;font-size:0.85rem;margin-bottom:1rem">
          {{ loadError }}
        </div>
      }

      @if (loadState === 'loaded' && !position) {
        <!-- Kein aktives Sparkonto -->
        <div style="background:white;padding:1.75rem;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);text-align:center">
          <div style="font-size:2.5rem;margin-bottom:0.75rem">&#128176;</div>
          <h2 style="font-size:1rem;font-weight:700;color:#1e293b;margin-bottom:0.5rem">
            Noch kein Sparkonto vorhanden
          </h2>
          <p style="color:#64748b;font-size:0.875rem;margin-bottom:1.5rem;line-height:1.5">
            Legen Sie jetzt Ihren Sparbetrag an und profitieren Sie von tagesaktuellem Ertrag –
            flexibel und ohne Mindestlaufzeit.
          </p>

          @if (depositError) {
            <div style="padding:0.75rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;color:#991b1b;font-size:0.85rem;margin-bottom:1rem;text-align:left">
              {{ depositError }}
            </div>
          }

          <button (click)="openDeposit()"
                  style="padding:0.75rem 2rem;background:#2563eb;color:white;border:none;border-radius:6px;font-weight:600;font-size:0.95rem;cursor:pointer">
            Jetzt Sparbetrag anlegen
          </button>
        </div>

        @if (showDepositConfirm) {
          <div style="margin-top:1rem;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:1.25rem">
            <p style="font-size:0.875rem;color:#374151;margin-bottom:1rem">
              Wie viel möchten Sie anlegen? Die Einlage wird von Ihrem Referenzkonto belastet.
            </p>
            <div style="margin-bottom:1rem">
              <label style="display:block;font-size:0.8rem;font-weight:600;color:#374151;margin-bottom:0.375rem">Betrag (EUR)</label>
              <input type="number" [(ngModel)]="depositAmount" min="1" step="0.01"
                     placeholder="z.B. 1000"
                     style="width:100%;box-sizing:border-box;padding:0.5rem 0.75rem;border:1px solid #e2e8f0;border-radius:6px;font-size:0.875rem">
              @if (depositAmount && depositAmount > 0) {
                <div style="font-size:0.75rem;color:#6b7280;margin-top:0.375rem">
                  Tagesertrag bei 3,5% p.a.: ca. <strong>{{ (depositAmount * 0.035 / 365) | number:'1.2-2' }} EUR</strong>
                </div>
              }
            </div>
            <div style="display:flex;gap:0.75rem">
              <button (click)="submitDeposit()" [disabled]="depositSubmitting || !depositAmount || depositAmount <= 0"
                      style="padding:0.625rem 1.25rem;background:#2563eb;color:white;border:none;border-radius:6px;font-weight:600;font-size:0.875rem;cursor:pointer">
                {{ depositSubmitting ? 'Wird angelegt…' : 'Bestätigen' }}
              </button>
              <button (click)="showDepositConfirm = false"
                      style="padding:0.625rem 1.25rem;background:white;color:#374151;border:1px solid #e2e8f0;border-radius:6px;font-weight:500;font-size:0.875rem;cursor:pointer">
                Abbrechen
              </button>
            </div>
          </div>
        }
      }

      @if (loadState === 'loaded' && position) {
        <!-- Aktives Sparkonto -->
        <div style="background:white;padding:1.75rem;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08)">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:1.25rem">
            <div>
              <div style="font-size:0.75rem;font-weight:600;color:#64748b;text-transform:uppercase;letter-spacing:0.05em;margin-bottom:0.25rem">
                Aktuelles Guthaben
              </div>
              <div style="font-size:2rem;font-weight:700;color:#1e293b">
                {{ position.currentValueEur | number:'1.2-2' }}&nbsp;EUR
              </div>
            </div>
            <span style="padding:0.25rem 0.75rem;background:#dcfce7;color:#166534;border-radius:9999px;font-size:0.75rem;font-weight:600">
              {{ position.status }}
            </span>
          </div>

          <div style="display:grid;grid-template-columns:1fr 1fr;gap:0;border:1px solid #e2e8f0;border-radius:6px;overflow:hidden;margin-bottom:1.25rem">
            <div style="padding:0.875rem;border-right:1px solid #e2e8f0">
              <div style="font-size:0.75rem;color:#64748b;margin-bottom:0.25rem">Eingezahlter Betrag</div>
              <div style="font-size:1rem;font-weight:600;color:#1e293b">{{ position.amountEur | number:'1.2-2' }}&nbsp;EUR</div>
            </div>
            <div style="padding:0.875rem">
              <div style="font-size:0.75rem;color:#64748b;margin-bottom:0.25rem">Tagesertrag</div>
              <div style="font-size:1rem;font-weight:600;color:#16a34a">+{{ position.dailyYieldEur | number:'1.2-4' }}&nbsp;EUR</div>
            </div>
            <div style="padding:0.875rem;border-top:1px solid #e2e8f0;border-right:1px solid #e2e8f0">
              <div style="font-size:0.75rem;color:#64748b;margin-bottom:0.25rem">Jahreszins (p.a.)</div>
              <div style="font-size:1rem;font-weight:600;color:#1e293b">{{ position.yieldRatePercent | number:'1.2-2' }}&nbsp;%</div>
            </div>
            <div style="padding:0.875rem;border-top:1px solid #e2e8f0">
              <div style="font-size:0.75rem;color:#64748b;margin-bottom:0.25rem">Einlagedatum</div>
              <div style="font-size:1rem;font-weight:600;color:#1e293b">{{ position.depositDate | date:'dd.MM.yyyy' }}</div>
            </div>
          </div>

          @if (redeemError) {
            <div style="padding:0.75rem;background:#fef2f2;border:1px solid #fecaca;border-radius:6px;color:#991b1b;font-size:0.85rem;margin-bottom:1rem">
              {{ redeemError }}
            </div>
          }

          @if (!showRedeemConfirm) {
            <button (click)="showRedeemConfirm = true"
                    style="width:100%;padding:0.625rem;background:white;color:#dc2626;border:1px solid #fecaca;border-radius:6px;font-weight:500;font-size:0.875rem;cursor:pointer">
              Sparkonto auflösen
            </button>
          } @else {
            <div style="background:#fef9ec;border:1px solid #fde68a;border-radius:6px;padding:1rem;margin-bottom:0.75rem">
              <p style="font-size:0.875rem;color:#92400e;margin-bottom:0.75rem">
                Sind Sie sicher, dass Sie das Sparkonto auflösen möchten?
                Das Guthaben wird auf Ihr Referenzkonto überwiesen.
              </p>
              <div style="display:flex;gap:0.75rem">
                <button (click)="confirmRedeem()" [disabled]="redeemSubmitting"
                        style="padding:0.5rem 1rem;background:#dc2626;color:white;border:none;border-radius:6px;font-weight:600;font-size:0.875rem;cursor:pointer">
                  {{ redeemSubmitting ? 'Wird aufgelöst…' : 'Ja, auflösen' }}
                </button>
                <button (click)="showRedeemConfirm = false"
                        style="padding:0.5rem 1rem;background:white;color:#374151;border:1px solid #e2e8f0;border-radius:6px;font-weight:500;font-size:0.875rem;cursor:pointer">
                  Abbrechen
                </button>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class YieldPositionComponent implements OnInit {
  private txService = inject(TransactionService);
  private auth = inject(AuthService);

  loadState: LoadState = 'loading';
  loadError: string | null = null;
  position: YieldPositionResponse | null = null;

  depositSubmitting = false;
  depositError: string | null = null;
  showDepositConfirm = false;
  depositAmount: number | null = null;

  redeemSubmitting = false;
  redeemError: string | null = null;
  showRedeemConfirm = false;

  ngOnInit(): void {
    this.loadPosition();
  }

  private loadPosition(): void {
    this.loadState = 'loading';
    this.loadError = null;

    this.txService.getYieldPosition().subscribe({
      next: (pos) => {
        this.loadState = 'loaded';
        this.position = pos;
      },
      error: (err: { status: number; error?: { message?: string } }) => {
        if (err.status === 404) {
          // Kein Sparkonto vorhanden – das ist ein normaler Zustand
          this.loadState = 'loaded';
          this.position = null;
        } else {
          this.loadState = 'error';
          this.loadError = err.error?.message ?? 'Kontodaten konnten nicht geladen werden.';
        }
      }
    });
  }

  openDeposit(): void {
    this.depositAmount = null;
    this.showDepositConfirm = true;
  }

  submitDeposit(): void {
    if (!this.depositAmount || this.depositAmount <= 0) return;
    this.depositSubmitting = true;
    this.depositError = null;

    const idempotencyKey = crypto.randomUUID();
    const iban = this.auth.getIban();

    this.txService.depositYield(idempotencyKey, iban, this.depositAmount).subscribe({
      next: (pos) => {
        this.depositSubmitting = false;
        this.showDepositConfirm = false;
        this.position = pos;
      },
      error: (err: { error?: { message?: string } }) => {
        this.depositSubmitting = false;
        this.depositError = err.error?.message ?? 'Das Sparkonto konnte nicht eröffnet werden. Bitte versuchen Sie es erneut.';
      }
    });
  }

  confirmRedeem(): void {
    if (!this.position) return;
    this.redeemSubmitting = true;
    this.redeemError = null;

    this.txService.redeemYield(this.position.positionId).subscribe({
      next: () => {
        this.redeemSubmitting = false;
        this.showRedeemConfirm = false;
        this.position = null;
      },
      error: (err: { error?: { message?: string } }) => {
        this.redeemSubmitting = false;
        this.redeemError = err.error?.message ?? 'Das Sparkonto konnte nicht aufgelöst werden. Bitte versuchen Sie es erneut.';
      }
    });
  }
}
