import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <nav style="background:#1a1a2e;padding:0.875rem 2rem;display:flex;align-items:center;gap:2.5rem;box-shadow:0 2px 8px rgba(0,0,0,0.3)">
      <span style="font-weight:700;font-size:1rem;color:white;letter-spacing:0.02em">
        Atruvia · Stablecoin Platform
      </span>
      <div style="display:flex;gap:1.5rem;flex:1;align-items:center">
        <!-- Firmenkunden -->
        <span style="color:#475569;font-size:0.7rem;font-weight:700;text-transform:uppercase;letter-spacing:0.07em">
          Firmenkunden
        </span>
        <a routerLink="/b2b/transfers"
           routerLinkActive="nav-active"
           style="font-size:0.875rem;font-weight:500;transition:color 0.15s"
           [style.color]="isB2bActive ? '#60a5fa' : '#94a3b8'">
          Überweisungen
        </a>
        <a routerLink="/b2b/transfers/new"
           style="color:#94a3b8;font-size:0.875rem;font-weight:500">
          Neue Überweisung
        </a>
        <a routerLink="/b2b/approvals"
           style="color:#94a3b8;font-size:0.875rem;font-weight:500">
          Freigaben
        </a>
        <a routerLink="/b2b/address-book"
           style="color:#94a3b8;font-size:0.875rem;font-weight:500">
          Adressbuch
        </a>

        <!-- Trennlinie -->
        <span style="width:1px;height:1.25rem;background:#334155;flex-shrink:0"></span>

        <!-- Privatkunden -->
        <span style="color:#475569;font-size:0.7rem;font-weight:700;text-transform:uppercase;letter-spacing:0.07em">
          Privatkunden
        </span>
        <a routerLink="/b2c/remittances"
           routerLinkActive="nav-active"
           style="color:#94a3b8;font-size:0.875rem;font-weight:500;transition:color 0.15s">
          Auslandsüberweisung
        </a>
        <a routerLink="/b2c/p2p"
           routerLinkActive="nav-active"
           style="color:#94a3b8;font-size:0.875rem;font-weight:500;transition:color 0.15s">
          Telefonüberweisung
        </a>
        <a routerLink="/b2c/yield"
           routerLinkActive="nav-active"
           style="color:#94a3b8;font-size:0.875rem;font-weight:500;transition:color 0.15s">
          Sparkonto
        </a>
      </div>
      <span style="color:#475569;font-size:0.75rem">dev-profil aktiv</span>
    </nav>
    <main style="padding:2rem;max-width:1200px;margin:0 auto">
      <router-outlet />
    </main>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100vh; }
  `]
})
export class AppComponent {
  isB2bActive = false;
}
