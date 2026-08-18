import { Component, OnInit } from '@angular/core';
import { Router, RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  template: `
    <!-- Dev-Portal: kein globales Nav nötig, die Komponente hat ihr eigenes Layout -->
    <ng-container *ngIf="!isDevPortal">
      <nav style="background:#1a1a2e;padding:0.75rem 2rem;display:flex;align-items:center;
                  gap:1.5rem;box-shadow:0 2px 8px rgba(0,0,0,0.3);flex-wrap:wrap;">

        <!-- Brand + Portal-Link -->
        <a routerLink="/dev-portal"
           style="font-weight:700;font-size:0.95rem;color:white;letter-spacing:0.02em;
                  text-decoration:none;display:flex;align-items:center;gap:0.4rem;flex-shrink:0;">
          ⚡ Atruvia · Stablecoin
        </a>

        <!-- Portal-Schnellzugriff -->
        <a routerLink="/dev-portal"
           style="background:#1e3a5f;color:#60a5fa;border:1px solid #2563eb;border-radius:6px;
                  font-size:0.7rem;font-weight:700;padding:0.2rem 0.6rem;text-decoration:none;flex-shrink:0;">
          📋 Dev-Portal
        </a>

        <div style="display:flex;gap:1rem;flex:1;align-items:center;flex-wrap:wrap;">

          <!-- B2B Gruppe -->
          <span style="color:#475569;font-size:0.65rem;font-weight:700;text-transform:uppercase;letter-spacing:0.07em;flex-shrink:0;">
            B2B
          </span>
          <a routerLink="/b2b/transfers" routerLinkActive="nav-active"
             style="color:#94a3b8;font-size:0.8rem;font-weight:500;text-decoration:none;flex-shrink:0;"
             [style.color]="isActive('/b2b/transfers') ? '#60a5fa' : '#94a3b8'">
            Überweisungen
          </a>
          <a routerLink="/b2b/transfers/new"
             style="color:#94a3b8;font-size:0.8rem;font-weight:500;text-decoration:none;flex-shrink:0;"
             [style.color]="isActive('/b2b/transfers/new') ? '#60a5fa' : '#94a3b8'">
            + Neu
          </a>
          <a routerLink="/b2b/approvals"
             style="color:#94a3b8;font-size:0.8rem;font-weight:500;text-decoration:none;flex-shrink:0;"
             [style.color]="isActive('/b2b/approvals') ? '#60a5fa' : '#94a3b8'">
            Freigaben
          </a>
          <a routerLink="/b2b/address-book"
             style="color:#94a3b8;font-size:0.8rem;font-weight:500;text-decoration:none;flex-shrink:0;"
             [style.color]="isActive('/b2b/address-book') ? '#60a5fa' : '#94a3b8'">
            Adressbuch
          </a>

          <!-- Trennlinie -->
          <span style="width:1px;height:1rem;background:#334155;flex-shrink:0;"></span>

          <!-- B2C Gruppe -->
          <span style="color:#475569;font-size:0.65rem;font-weight:700;text-transform:uppercase;letter-spacing:0.07em;flex-shrink:0;">
            B2C
          </span>
          <a routerLink="/b2c/remittances"
             style="color:#94a3b8;font-size:0.8rem;font-weight:500;text-decoration:none;flex-shrink:0;"
             [style.color]="isActive('/b2c/remittances') ? '#c4b5fd' : '#94a3b8'">
            Remittance
          </a>
          <a routerLink="/b2c/p2p"
             style="color:#94a3b8;font-size:0.8rem;font-weight:500;text-decoration:none;flex-shrink:0;"
             [style.color]="isActive('/b2c/p2p') ? '#c4b5fd' : '#94a3b8'">
            P2P
          </a>
          <a routerLink="/b2c/yield"
             style="color:#94a3b8;font-size:0.8rem;font-weight:500;text-decoration:none;flex-shrink:0;"
             [style.color]="isActive('/b2c/yield') ? '#c4b5fd' : '#94a3b8'">
            Yield
          </a>
        </div>

        <!-- User Tenant Badge -->
        <div *ngIf="currentUserLabel"
             style="font-size:0.65rem;font-weight:600;background:#1e3a5f;color:#60a5fa;
                    border:1px solid #2563eb;border-radius:6px;padding:0.2rem 0.6rem;
                    white-space:nowrap;flex-shrink:0;">
          {{ currentUserLabel }}
        </div>

        <span style="color:#475569;font-size:0.65rem;flex-shrink:0;">dev</span>
        <button (click)="logout()"
                style="background:none;border:1px solid #334155;border-radius:6px;
                       color:#94a3b8;font-size:0.7rem;padding:0.2rem 0.6rem;cursor:pointer;flex-shrink:0;">
          Abmelden
        </button>
      </nav>
    </ng-container>

    <!-- Router-Outlet: Dev-Portal füllt volle Seite, andere Seiten mit max-width -->
    <ng-container *ngIf="isDevPortal; else normalLayout">
      <router-outlet />
    </ng-container>
    <ng-template #normalLayout>
      <main style="padding:2rem;max-width:1200px;margin:0 auto;">
        <router-outlet />
      </main>
    </ng-template>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100vh; }
    .nav-active { color: #60a5fa !important; }
  `]
})
export class AppComponent implements OnInit {

  currentUserLabel = '';
  isDevPortal = false;

  constructor(private router: Router) {}

  ngOnInit() {
    this.updateState();
    this.router.events.subscribe(() => this.updateState());
  }

  updateState() {
    this.isDevPortal = this.router.url === '/dev-portal' || this.router.url === '/';
    const token = localStorage.getItem('access_token');
    const tenantId = localStorage.getItem('tenant_id') ?? '';
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const tenantMap: Record<string, string> = {
          'tenant-kleine-vb': '🟢 VB Kleinstadt',
          'tenant-grosse-vb': '🔵 VB Metropole',
          'tenant-marktbank': '🟣 Marktbank'
        };
        this.currentUserLabel = `${tenantMap[tenantId] ?? tenantId} · ${payload.sub}`;
      } catch {
        this.currentUserLabel = '';
      }
    } else {
      this.currentUserLabel = '';
    }
  }

  isActive(path: string): boolean {
    return this.router.url.startsWith(path);
  }

  logout(): void {
    localStorage.removeItem('access_token');
    localStorage.removeItem('tenant_id');
    sessionStorage.removeItem('playbook_prefill');
    this.router.navigate(['/dev-portal']);
  }
}
