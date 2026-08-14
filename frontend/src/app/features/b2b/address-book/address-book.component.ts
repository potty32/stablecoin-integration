import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import {
  TransactionService,
  AddressBookEntry
} from '../../../core/services/transaction.service';

function ethereumAddressValidator(control: AbstractControl): { invalidAddress: true } | null {
  const val: string = control.value ?? '';
  return /^0x[0-9a-fA-F]{40}$/.test(val) ? null : { invalidAddress: true };
}

@Component({
  selector: 'app-address-book',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div>
      <!-- Header -->
      <h1 style="font-size:1.375rem;font-weight:700;margin:0 0 1.5rem 0">Adressbuch</h1>

      <div style="display:grid;grid-template-columns:1fr 360px;gap:2rem;align-items:flex-start">

        <!-- Left: Address list -->
        <div>
          <!-- Loading -->
          <div *ngIf="loading" style="text-align:center;padding:3rem;color:#94a3b8">
            Wird geladen…
          </div>

          <!-- Error -->
          <div *ngIf="loadError"
               style="background:#fee2e2;border:1px solid #fca5a5;border-radius:8px;padding:1rem;color:#b91c1c;font-size:0.875rem;margin-bottom:1rem">
            {{ loadError }}
          </div>

          <!-- Empty -->
          <div *ngIf="!loading && addresses.length === 0 && !loadError"
               style="background:white;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);padding:3rem;text-align:center;color:#94a3b8">
            <div style="font-size:1.5rem;margin-bottom:0.5rem">📋</div>
            <p style="margin:0;font-size:0.875rem">Noch keine Adressen gespeichert.</p>
          </div>

          <!-- Cards -->
          <div *ngIf="!loading" style="display:flex;flex-direction:column;gap:0.75rem">
            <div *ngFor="let addr of addresses"
                 style="background:white;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);padding:1rem 1.25rem;display:flex;justify-content:space-between;align-items:center">
              <div style="flex:1;min-width:0">
                <div style="display:flex;align-items:center;gap:0.75rem;margin-bottom:0.375rem">
                  <span style="font-weight:600;font-size:0.9375rem">{{ addr.label }}</span>
                  <span style="font-size:0.75rem;font-weight:600;padding:2px 8px;border-radius:4px;background:#eff6ff;color:#1d4ed8">
                    {{ addr.currency }}
                  </span>
                  <span [style]="riskStyle(addr.riskScore)">{{ addr.riskScore }}</span>
                </div>
                <div style="font-family:monospace;font-size:0.78rem;color:#64748b;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
                  {{ addr.walletAddress }}
                </div>
                <div style="font-size:0.75rem;color:#94a3b8;margin-top:0.25rem">
                  Status: {{ addr.status }}
                  <span *ngIf="addr.verifiedAt"> · Verifiziert: {{ addr.verifiedAt | date:'dd.MM.yyyy' }}</span>
                </div>
              </div>
              <div style="margin-left:1rem">
                <button (click)="revoke(addr.id)"
                        [disabled]="revoking === addr.id"
                        style="border:1px solid #fca5a5;background:white;color:#dc2626;padding:0.375rem 0.875rem;border-radius:6px;font-size:0.8rem;cursor:pointer;font-weight:500;white-space:nowrap"
                        [style.opacity]="revoking === addr.id ? '0.5' : '1'">
                  Sperren
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Right: Add form -->
        <div style="background:white;border-radius:8px;box-shadow:0 1px 3px rgba(0,0,0,0.08);padding:1.5rem;position:sticky;top:1rem">
          <h2 style="font-size:1rem;font-weight:600;margin:0 0 1.25rem 0">Neue Adresse hinzufügen</h2>

          <form [formGroup]="form" (ngSubmit)="submit()" style="display:flex;flex-direction:column;gap:1rem">

            <!-- Label -->
            <div>
              <label style="display:block;font-size:0.8125rem;font-weight:500;margin-bottom:0.375rem;color:#374151">
                Bezeichnung
              </label>
              <input formControlName="label" type="text" placeholder="z. B. Lieferant München GmbH"
                     style="width:100%;border:1px solid #d1d5db;border-radius:6px;padding:0.5rem 0.75rem;font-size:0.875rem;box-sizing:border-box"
                     [style.border-color]="fieldInvalid('label') ? '#fca5a5' : '#d1d5db'" />
              <p *ngIf="fieldInvalid('label')"
                 style="margin:0.25rem 0 0 0;font-size:0.75rem;color:#dc2626">
                Pflichtfeld (mind. 2 Zeichen)
              </p>
            </div>

            <!-- Wallet address -->
            <div>
              <label style="display:block;font-size:0.8125rem;font-weight:500;margin-bottom:0.375rem;color:#374151">
                Wallet-Adresse (0x…)
              </label>
              <input formControlName="walletAddress" type="text" placeholder="0xabc123…"
                     style="width:100%;border:1px solid #d1d5db;border-radius:6px;padding:0.5rem 0.75rem;font-size:0.875rem;font-family:monospace;box-sizing:border-box"
                     [style.border-color]="fieldInvalid('walletAddress') ? '#fca5a5' : '#d1d5db'" />
              <p *ngIf="fieldInvalid('walletAddress')"
                 style="margin:0.25rem 0 0 0;font-size:0.75rem;color:#dc2626">
                Gültige Ethereum-Adresse erforderlich (0x + 40 Hex-Zeichen)
              </p>
            </div>

            <!-- Currency -->
            <div>
              <label style="display:block;font-size:0.8125rem;font-weight:500;margin-bottom:0.375rem;color:#374151">
                Währung
              </label>
              <select formControlName="currency"
                      style="width:100%;border:1px solid #d1d5db;border-radius:6px;padding:0.5rem 0.75rem;font-size:0.875rem;background:white;box-sizing:border-box">
                <option value="USDC">USDC</option>
                <option value="EURC">EURC</option>
              </select>
            </div>

            <!-- Submit error -->
            <div *ngIf="submitError"
                 style="background:#fee2e2;border:1px solid #fca5a5;border-radius:6px;padding:0.75rem;font-size:0.8rem;color:#b91c1c">
              {{ submitError }}
            </div>

            <!-- Submit success -->
            <div *ngIf="submitSuccess"
                 style="background:#dcfce7;border:1px solid #86efac;border-radius:6px;padding:0.75rem;font-size:0.8rem;color:#166534">
              Adresse erfolgreich hinzugefügt.
            </div>

            <button type="submit" [disabled]="submitting"
                    style="background:#2563eb;color:white;border:none;padding:0.5rem;border-radius:6px;font-size:0.875rem;font-weight:500;cursor:pointer;width:100%"
                    [style.opacity]="submitting ? '0.6' : '1'">
              {{ submitting ? 'Wird gespeichert…' : 'Adresse speichern' }}
            </button>
          </form>
        </div>

      </div>
    </div>
  `
})
export class AddressBookComponent implements OnInit {
  private readonly txService = inject(TransactionService);
  private readonly fb = inject(FormBuilder);

  addresses: AddressBookEntry[] = [];
  loading = false;
  loadError = '';
  revoking = '';
  submitting = false;
  submitError = '';
  submitSuccess = false;

  form: FormGroup = this.fb.group({
    label: ['', [Validators.required, Validators.minLength(2)]],
    walletAddress: ['', [Validators.required, ethereumAddressValidator]],
    currency: ['USDC', Validators.required]
  });

  ngOnInit(): void {
    this.loadAddresses();
  }

  loadAddresses(): void {
    this.loading = true;
    this.loadError = '';
    this.txService.listAddressBook().subscribe({
      next: entries => {
        this.addresses = entries;
        this.loading = false;
      },
      error: (err: { message?: string }) => {
        this.loadError = err.message ?? 'Fehler beim Laden des Adressbuchs.';
        this.loading = false;
      }
    });
  }

  fieldInvalid(field: string): boolean {
    const ctrl = this.form.get(field);
    return !!(ctrl && ctrl.invalid && (ctrl.dirty || ctrl.touched));
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.submitError = '';
    this.submitSuccess = false;

    const { label, walletAddress, currency } = this.form.value as {
      label: string;
      walletAddress: string;
      currency: string;
    };

    this.txService.addAddress(label, walletAddress, currency).subscribe({
      next: () => {
        this.submitting = false;
        this.submitSuccess = true;
        this.form.reset({ currency: 'USDC' });
        this.loadAddresses();
        setTimeout(() => { this.submitSuccess = false; }, 4000);
      },
      error: (err: { message?: string }) => {
        this.submitting = false;
        this.submitError = err.message ?? 'Fehler beim Speichern der Adresse.';
      }
    });
  }

  revoke(id: string): void {
    if (!confirm('Adresse wirklich sperren?')) return;
    this.revoking = id;
    this.txService.revokeAddress(id).subscribe({
      next: () => {
        this.revoking = '';
        this.loadAddresses();
      },
      error: (err: { message?: string }) => {
        this.revoking = '';
        alert('Fehler: ' + (err.message ?? 'Unbekannter Fehler'));
      }
    });
  }

  riskStyle(riskScore: string): string {
    const base = 'font-size:0.72rem;font-weight:600;padding:2px 8px;border-radius:12px;';
    if (riskScore === 'LOW') return base + 'background:#dcfce7;color:#166534';
    if (riskScore === 'MEDIUM') return base + 'background:#fef9c3;color:#854d0e';
    if (riskScore === 'HIGH') return base + 'background:#fee2e2;color:#b91c1c';
    return base + 'background:#f1f5f9;color:#475569';
  }
}
