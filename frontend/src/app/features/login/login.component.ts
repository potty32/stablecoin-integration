import { Component } from '@angular/core';
import { Router } from '@angular/router';

const DEV_TOKENS: Record<string, string> = {
  b2b: 'eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiY3VzdC1iMmItMDAxIiwgImlhdCI6IDE3ODY3NjUyMDksICJleHAiOiAxNzg2ODUxNjA5fQ.oHtOJc6T1q3FE4uURwprZJhmPOhMhFFRjWSQFqYkguI',
  b2c: 'eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiY3VzdC1iMmMtMDAxIiwgImlhdCI6IDE3ODY3NjUyMDksICJleHAiOiAxNzg2ODUxNjA5fQ.tlbUfsiFn3gnFv4KT5xEkweluU3uVodWaPChSCGoBVc',
};

@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div style="min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0f172a">
      <div style="background:white;border-radius:12px;padding:2.5rem 3rem;width:100%;max-width:420px;box-shadow:0 20px 60px rgba(0,0,0,0.4)">

        <div style="text-align:center;margin-bottom:2rem">
          <div style="font-size:0.75rem;font-weight:700;letter-spacing:0.12em;color:#64748b;text-transform:uppercase;margin-bottom:0.5rem">
            Atruvia AG
          </div>
          <h1 style="font-size:1.5rem;font-weight:800;color:#0f172a;margin:0 0 0.5rem 0">
            Stablecoin Platform
          </h1>
          <p style="color:#64748b;font-size:0.875rem;margin:0">
            Dev-Umgebung — Profil wählen
          </p>
        </div>

        <div style="display:flex;flex-direction:column;gap:0.875rem">
          <button (click)="login('b2b')"
                  style="width:100%;padding:1rem 1.25rem;background:#2563eb;color:white;border:none;border-radius:8px;font-size:0.9375rem;font-weight:600;cursor:pointer;display:flex;align-items:center;gap:1rem;transition:background 0.15s"
                  (mouseenter)="hovered='b2b'" (mouseleave)="hovered=''"
                  [style.background]="hovered === 'b2b' ? '#1d4ed8' : '#2563eb'">
            <span style="font-size:1.5rem">🏢</span>
            <div style="text-align:left">
              <div>Als Firmenkunde anmelden</div>
              <div style="font-size:0.75rem;font-weight:400;opacity:0.85">cust-b2b-001 · Limit 25.000 EUR · KYC Tier 3</div>
            </div>
          </button>

          <button (click)="login('b2c')"
                  style="width:100%;padding:1rem 1.25rem;background:#0f172a;color:white;border:none;border-radius:8px;font-size:0.9375rem;font-weight:600;cursor:pointer;display:flex;align-items:center;gap:1rem;transition:background 0.15s"
                  (mouseenter)="hovered='b2c'" (mouseleave)="hovered=''"
                  [style.background]="hovered === 'b2c' ? '#1e293b' : '#0f172a'">
            <span style="font-size:1.5rem">👤</span>
            <div style="text-align:left">
              <div>Als Privatkunde anmelden</div>
              <div style="font-size:0.75rem;font-weight:400;opacity:0.85">cust-b2c-001 · Limit 5.000 EUR · KYC Tier 2</div>
            </div>
          </button>
        </div>

        <p style="margin:1.75rem 0 0 0;text-align:center;font-size:0.75rem;color:#94a3b8">
          Dev-Profil aktiv · Keine echten Transaktionen
        </p>
      </div>
    </div>
  `
})
export class LoginComponent {
  hovered = '';

  constructor(private router: Router) {}

  login(role: 'b2b' | 'b2c'): void {
    localStorage.setItem('access_token', DEV_TOKENS[role]);
    this.router.navigate([role === 'b2b' ? '/b2b/transfers' : '/b2c/remittances']);
  }
}
