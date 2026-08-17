import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

interface DevTokenResponse {
  token: string;
  tenant: string;
  customerId: string;
}

const TENANTS = [
  { id: 'tenant-kleine-vb', label: 'Volksbank Kleinstadt eG' },
  { id: 'tenant-grosse-vb', label: 'Volksbank Metropole eG'  },
  { id: 'tenant-marktbank', label: 'Marktbank AG'            },
];

const CUSTOMER_IDS: Record<string, string> = {
  b2b: 'cust-b2b-001',
  b2c: 'cust-b2c-001',
};

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div style="min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f172a">
      <div style="background:white;border-radius:12px;padding:2.5rem 3rem;width:100%;max-width:440px;box-shadow:0 20px 60px rgba(0,0,0,0.4)">

        <div style="text-align:center;margin-bottom:1.75rem">
          <div style="font-size:0.75rem;font-weight:700;letter-spacing:0.12em;color:#64748b;text-transform:uppercase;margin-bottom:0.5rem">
            Atruvia AG
          </div>
          <h1 style="font-size:1.5rem;font-weight:800;color:#0f172a;margin:0 0 0.25rem 0">
            Stablecoin Platform
          </h1>
          <p style="color:#64748b;font-size:0.875rem;margin:0">
            Dev-Umgebung — Mandant und Profil wählen
          </p>
        </div>

        <!-- Mandanten-Selektor -->
        <div style="margin-bottom:1.25rem">
          <label style="display:block;font-size:0.8125rem;font-weight:600;color:#374151;margin-bottom:0.4rem">
            Mandant (Bank)
          </label>
          <select [(ngModel)]="selectedTenant"
                  style="width:100%;padding:0.6rem 0.875rem;border:1.5px solid #e2e8f0;border-radius:8px;font-size:0.9375rem;color:#0f172a;background:#f8fafc;cursor:pointer;outline:none">
            @for (t of tenants; track t.id) {
              <option [value]="t.id">{{ t.label }}</option>
            }
          </select>
          <div style="margin-top:0.3rem;font-size:0.7rem;color:#94a3b8">
            Mandanten-ID: {{ selectedTenant }}
          </div>
        </div>

        <!-- Login-Buttons -->
        <div style="display:flex;flex-direction:column;gap:0.875rem">
          <button (click)="login('b2b')"
                  [disabled]="loading"
                  style="width:100%;padding:1rem 1.25rem;background:#2563eb;color:white;border:none;border-radius:8px;font-size:0.9375rem;font-weight:600;cursor:pointer;display:flex;align-items:center;gap:1rem;transition:background 0.15s;opacity:1"
                  [style.opacity]="loading ? '0.7' : '1'"
                  (mouseenter)="hovered='b2b'" (mouseleave)="hovered=''"
                  [style.background]="hovered === 'b2b' ? '#1d4ed8' : '#2563eb'">
            <span style="font-size:1.5rem">🏢</span>
            <div style="text-align:left">
              <div>Als Firmenkunde anmelden</div>
              <div style="font-size:0.75rem;font-weight:400;opacity:0.85">cust-b2b-001 · Limit 25.000 EUR · KYC Tier 3</div>
            </div>
          </button>

          <button (click)="login('b2c')"
                  [disabled]="loading"
                  style="width:100%;padding:1rem 1.25rem;background:#0f172a;color:white;border:none;border-radius:8px;font-size:0.9375rem;font-weight:600;cursor:pointer;display:flex;align-items:center;gap:1rem;transition:background 0.15s"
                  [style.opacity]="loading ? '0.7' : '1'"
                  (mouseenter)="hovered='b2c'" (mouseleave)="hovered=''"
                  [style.background]="hovered === 'b2c' ? '#1e293b' : '#0f172a'">
            <span style="font-size:1.5rem">👤</span>
            <div style="text-align:left">
              <div>Als Privatkunde anmelden</div>
              <div style="font-size:0.75rem;font-weight:400;opacity:0.85">cust-b2c-001 · Limit 5.000 EUR · KYC Tier 2</div>
            </div>
          </button>
        </div>

        @if (errorMsg) {
          <div style="margin-top:1rem;padding:0.6rem 0.875rem;background:#fef2f2;border:1px solid #fca5a5;border-radius:6px;font-size:0.8125rem;color:#dc2626">
            {{ errorMsg }}
          </div>
        }

        <p style="margin:1.75rem 0 0 0;text-align:center;font-size:0.75rem;color:#94a3b8">
          Dev-Profil aktiv · Keine echten Transaktionen
        </p>
      </div>
    </div>
  `
})
export class LoginComponent {
  hovered = '';
  loading = false;
  errorMsg = '';
  selectedTenant = 'tenant-kleine-vb';
  tenants = TENANTS;

  constructor(private router: Router, private http: HttpClient) {}

  login(role: 'b2b' | 'b2c'): void {
    this.loading = true;
    this.errorMsg = '';
    const customerId = CUSTOMER_IDS[role];

    this.http.get<DevTokenResponse>(`/api/v1/auth/dev-token`, {
      params: { customerId, tenant: this.selectedTenant }
    }).subscribe({
      next: (res) => {
        localStorage.setItem('access_token', res.token);
        localStorage.setItem('tenant_id', res.tenant);
        this.loading = false;
        this.router.navigate([role === 'b2b' ? '/b2b/transfers' : '/b2c/remittances']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = `Login fehlgeschlagen: ${err.status} ${err.statusText}`;
      }
    });
  }
}
