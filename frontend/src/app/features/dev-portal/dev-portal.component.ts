import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { TransactionService } from '../../core/services/transaction.service';

// ── Typen ─────────────────────────────────────────────────────────────────────

interface Tenant {
  id: string;
  name: string;
  short: string;
  color: string;
  bg: string;
}

interface UserProfile {
  customerId: string;
  name: string;
  role: string;
  iban?: string;
  type: 'B2B' | 'B2C';
  tenantId: string;
}

interface TestDatum {
  label: string;
  value: string;
}

interface PlaybookEntry {
  id: string;
  emoji: string;
  title: string;
  description: string;
  regulatoryRef?: string;
  testData: TestDatum[];
  steps: string[];
  targetRoute: string;
  requiredType: 'B2B' | 'B2C' | 'ANY';
  prefillKey?: string;
  action?: () => void;
}

interface Tab {
  id: string;
  label: string;
  emoji: string;
  entries: PlaybookEntry[];
}

@Component({
  selector: 'app-dev-portal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
<div style="min-height:100vh; background:#0f172a; color:#e2e8f0; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">

  <!-- ── HEADER ────────────────────────────────────────────────────────────── -->
  <div style="background:linear-gradient(135deg,#1e3a5f 0%,#0f172a 100%);
              border-bottom:1px solid #334155; padding:1.5rem 2rem;">
    <div style="max-width:1400px;margin:0 auto;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:1rem;">
      <div>
        <div style="display:flex;align-items:center;gap:0.75rem;">
          <div style="width:40px;height:40px;background:#2563eb;border-radius:10px;
                      display:flex;align-items:center;justify-content:center;font-size:1.4rem;">⚡</div>
          <div>
            <h1 style="margin:0;font-size:1.5rem;font-weight:700;color:#f1f5f9;">
              Atruvia · Stablecoin Dev-Portal
            </h1>
            <p style="margin:0;font-size:0.75rem;color:#94a3b8;">
              Interactive Use Case Playbook · v3.0 · Flyway V1–V18
            </p>
          </div>
        </div>
      </div>

      <div style="display:flex;align-items:center;gap:1rem;flex-wrap:wrap;">
        <!-- Health Badge -->
        <div style="display:flex;align-items:center;gap:0.4rem;background:#1e293b;
                    border:1px solid #334155;border-radius:8px;padding:0.4rem 0.75rem;">
          <span [style.background]="healthStatus==='UP' ? '#22c55e' : healthStatus==='CHECKING' ? '#f59e0b' : '#ef4444'"
                style="width:8px;height:8px;border-radius:50%;display:inline-block;"></span>
          <span style="font-size:0.75rem;font-weight:600;">Backend {{ healthStatus }}</span>
        </div>

        <!-- Current User Badge -->
        <div *ngIf="currentUser" style="display:flex;align-items:center;gap:0.5rem;
                    border-radius:8px;padding:0.4rem 0.75rem;font-size:0.75rem;font-weight:600;"
             [style.background]="currentTenant?.bg"
             [style.color]="currentTenant?.color"
             [style.border]="'1px solid ' + (currentTenant?.color || '#334155')">
          <span>{{ currentTenant?.short }}</span>
          <span>·</span>
          <span>{{ currentUser.name }}</span>
          <span style="opacity:0.7">({{ currentUser.type }})</span>
        </div>

        <button (click)="goToDashboard()" *ngIf="currentUser"
                style="background:#2563eb;color:#fff;border:none;border-radius:8px;
                       padding:0.4rem 1rem;font-size:0.8rem;font-weight:600;cursor:pointer;">
          → Dashboard
        </button>

        <button (click)="logout()" *ngIf="currentUser"
                style="background:transparent;color:#94a3b8;border:1px solid #334155;
                       border-radius:8px;padding:0.4rem 0.75rem;font-size:0.75rem;cursor:pointer;">
          Abmelden
        </button>
      </div>
    </div>
  </div>

  <div style="max-width:1400px;margin:0 auto;padding:2rem;">

    <!-- ── ARCHITEKTUR-OVERVIEW ──────────────────────────────────────────── -->
    <section style="margin-bottom:2rem;">
      <h2 style="color:#94a3b8;font-size:0.7rem;letter-spacing:0.1em;text-transform:uppercase;
                 font-weight:600;margin-bottom:1rem;">Systemarchitektur — Dual-Rail + Multi-Tenancy RLS</h2>

      <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:1rem;margin-bottom:1rem;">

        <!-- SWIFT Rail -->
        <div style="background:#1e293b;border:1px solid #334155;border-radius:12px;padding:1.25rem;">
          <div style="font-size:0.6rem;color:#94a3b8;letter-spacing:0.08em;text-transform:uppercase;margin-bottom:0.5rem;">
            KLASSISCHE SCHIENE
          </div>
          <div style="font-size:1rem;font-weight:700;color:#f1f5f9;margin-bottom:0.5rem;">🏦 SWIFT-Schiene</div>
          <div style="font-size:0.75rem;color:#64748b;line-height:1.6;">
            T+1 bis T+3 Abwicklung<br>
            SEPA-Überweisungen<br>
            Kernbanksystem (IS-B)<br>
            <span style="color:#ef4444;font-weight:600;">⚠ Gebühren + Laufzeit</span>
          </div>
        </div>

        <!-- Turbo Rail -->
        <div style="background:#1e293b;border:1px solid #2563eb;border-radius:12px;padding:1.25rem;
                    box-shadow:0 0 20px rgba(37,99,235,0.15);">
          <div style="font-size:0.6rem;color:#60a5fa;letter-spacing:0.08em;text-transform:uppercase;margin-bottom:0.5rem;">
            STABLECOIN-SCHIENE ⚡
          </div>
          <div style="font-size:1rem;font-weight:700;color:#93c5fd;margin-bottom:0.5rem;">🔵 Turbo Rail</div>
          <div style="font-size:0.75rem;color:#94a3b8;line-height:1.6;">
            T+0 · Blockchain-Settlement<br>
            Circle API + Taurus Custody<br>
            USDC / EURC on Polygon<br>
            <span style="color:#22c55e;font-weight:600;">✓ Echtzeit + MiCA-konform</span>
          </div>
        </div>

        <!-- RLS Multi-Tenancy -->
        <div style="background:#1e293b;border:1px solid #334155;border-radius:12px;padding:1.25rem;">
          <div style="font-size:0.6rem;color:#94a3b8;letter-spacing:0.08em;text-transform:uppercase;margin-bottom:0.5rem;">
            MANDANTENARCHITEKTUR
          </div>
          <div style="font-size:1rem;font-weight:700;color:#f1f5f9;margin-bottom:0.5rem;">🔐 PostgreSQL RLS</div>
          <div style="font-size:0.75rem;line-height:1.6;">
            <div style="display:flex;gap:0.3rem;margin-bottom:0.2rem;">
              <span style="background:#14532d;color:#86efac;border-radius:4px;padding:0 0.4rem;font-size:0.65rem;">VB Kleinstadt</span>
              <span style="background:#1e3a5f;color:#93c5fd;border-radius:4px;padding:0 0.4rem;font-size:0.65rem;">VB Metropole</span>
            </div>
            <span style="background:#4a1d96;color:#c4b5fd;border-radius:4px;padding:0 0.4rem;font-size:0.65rem;">Marktbank AG</span>
            <div style="color:#64748b;margin-top:0.4rem;">JWT tenant-Claim → RLS-Policy<br>= vollständige Datentrennung</div>
          </div>
        </div>
      </div>

      <!-- Flow Diagram -->
      <div style="background:#1e293b;border:1px solid #334155;border-radius:12px;padding:1rem;
                  display:flex;align-items:center;justify-content:center;gap:0.5rem;flex-wrap:wrap;
                  font-size:0.75rem;">
        <span style="background:#1e3a5f;color:#93c5fd;border-radius:6px;padding:0.3rem 0.6rem;">Volksbank ERP / SAP</span>
        <span style="color:#334155;">→</span>
        <span style="background:#1e293b;border:1px solid #2563eb;color:#60a5fa;border-radius:6px;padding:0.3rem 0.6rem;">Stablecoin API (Spring Boot)</span>
        <span style="color:#334155;">→</span>
        <span style="background:#1c1917;color:#d6d3d1;border-radius:6px;padding:0.3rem 0.6rem;">Taurus Custody (Signatur)</span>
        <span style="color:#334155;">→</span>
        <span style="background:#172554;color:#bfdbfe;border-radius:6px;padding:0.3rem 0.6rem;">Circle (On-Chain Submit)</span>
        <span style="color:#334155;">→</span>
        <span style="background:#064e3b;color:#6ee7b7;border-radius:6px;padding:0.3rem 0.6rem;">Polygon Blockchain ✓</span>
      </div>
    </section>

    <!-- ── LOGIN GATE ─────────────────────────────────────────────────────── -->
    <section style="margin-bottom:2rem;background:#1e293b;border:1px solid #334155;
                    border-radius:12px;padding:1.5rem;">
      <h2 style="margin:0 0 1rem;font-size:1rem;font-weight:700;color:#f1f5f9;">
        🔑 Mandant & Benutzer auswählen
      </h2>

      <div style="display:grid;grid-template-columns:1fr 1fr auto;gap:1rem;align-items:end;flex-wrap:wrap;">

        <!-- Tenant Dropdown -->
        <div>
          <label style="display:block;font-size:0.75rem;color:#94a3b8;margin-bottom:0.4rem;font-weight:600;">
            Mandant (Volksbank)
          </label>
          <select [(ngModel)]="selectedTenantId" (change)="onTenantChange()"
                  style="width:100%;background:#0f172a;color:#e2e8f0;border:1px solid #475569;
                         border-radius:8px;padding:0.6rem 0.75rem;font-size:0.875rem;">
            <option *ngFor="let t of tenants" [value]="t.id">{{ t.name }}</option>
          </select>
        </div>

        <!-- User Dropdown -->
        <div>
          <label style="display:block;font-size:0.75rem;color:#94a3b8;margin-bottom:0.4rem;font-weight:600;">
            Benutzer / Rolle
          </label>
          <select [(ngModel)]="selectedUserId"
                  style="width:100%;background:#0f172a;color:#e2e8f0;border:1px solid #475569;
                         border-radius:8px;padding:0.6rem 0.75rem;font-size:0.875rem;">
            <option *ngFor="let u of filteredUsers" [value]="u.customerId">
              {{ u.name }} · {{ u.role }} ({{ u.type }})
            </option>
          </select>
        </div>

        <!-- Login Button -->
        <div>
          <button (click)="login()"
                  [disabled]="loginLoading"
                  style="background:#2563eb;color:#fff;border:none;border-radius:8px;
                         padding:0.6rem 1.5rem;font-size:0.875rem;font-weight:600;cursor:pointer;
                         white-space:nowrap;width:100%;">
            {{ loginLoading ? '⏳ Anmelden...' : '🚀 Als Testnutzer einloggen' }}
          </button>
        </div>
      </div>

      <!-- Login Error -->
      <div *ngIf="loginError"
           style="margin-top:0.75rem;background:#450a0a;border:1px solid #dc2626;
                  border-radius:8px;padding:0.75rem;color:#fca5a5;font-size:0.8rem;">
        ❌ {{ loginError }}
      </div>

      <!-- Login Success Banner -->
      <div *ngIf="currentUser"
           style="margin-top:0.75rem;border-radius:8px;padding:0.75rem;font-size:0.8rem;
                  display:flex;align-items:center;gap:0.75rem;"
           [style.background]="currentTenant?.bg + '33'"
           [style.border]="'1px solid ' + currentTenant?.color">
        <span style="font-size:1.2rem;">✅</span>
        <div>
          <strong [style.color]="currentTenant?.color">{{ currentUser.name }}</strong>
          <span style="color:#94a3b8;"> · {{ currentTenant?.name }} · {{ currentUser.type }}</span>
          <span *ngIf="currentUser.iban" style="color:#64748b;"> · IBAN: {{ currentUser.iban }}</span>
        </div>
      </div>
    </section>

    <!-- ── PLAYBOOK TABS ───────────────────────────────────────────────────── -->
    <section>
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:1rem;">
        <h2 style="margin:0;font-size:1rem;font-weight:700;color:#f1f5f9;">
          📋 Interaktives Use Case Playbook
          <span style="font-size:0.75rem;color:#64748b;font-weight:400;margin-left:0.5rem;">
            ({{ totalUseCase }} Use Cases · {{ missingUiCount }} ohne UI → Dev-Portal-Trigger)
          </span>
        </h2>
        <div style="display:flex;gap:0.5rem;">
          <input [(ngModel)]="ucFilter" placeholder="Use Case suchen..."
                 style="background:#1e293b;color:#e2e8f0;border:1px solid #334155;border-radius:8px;
                        padding:0.4rem 0.75rem;font-size:0.8rem;width:180px;" />
        </div>
      </div>

      <!-- Tab Bar -->
      <div style="display:flex;gap:0.25rem;margin-bottom:1rem;overflow-x:auto;padding-bottom:0.25rem;">
        <button *ngFor="let tab of tabs" (click)="activeTab=tab.id"
                [style.background]="activeTab===tab.id ? '#2563eb' : '#1e293b'"
                [style.color]="activeTab===tab.id ? '#fff' : '#94a3b8'"
                [style.border]="activeTab===tab.id ? '1px solid #2563eb' : '1px solid #334155'"
                style="border-radius:8px;padding:0.5rem 1rem;font-size:0.8rem;font-weight:600;
                       cursor:pointer;white-space:nowrap;">
          {{ tab.emoji }} {{ tab.label }}
          <span style="font-size:0.65rem;opacity:0.7;margin-left:0.3rem;">({{ tab.entries.length }})</span>
        </button>
      </div>

      <!-- UC Cards Grid -->
      <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:1rem;">
        <div *ngFor="let uc of filteredEntries"
             style="background:#1e293b;border:1px solid #334155;border-radius:12px;
                    padding:1.25rem;display:flex;flex-direction:column;gap:0.75rem;
                    transition:border-color 0.2s;"
             [style.borderColor]="hoveredUc===uc.id ? '#2563eb' : '#334155'"
             (mouseenter)="hoveredUc=uc.id" (mouseleave)="hoveredUc=''">

          <!-- UC Header -->
          <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:0.5rem;">
            <div>
              <div style="display:flex;align-items:center;gap:0.5rem;margin-bottom:0.25rem;">
                <span style="font-size:1.2rem;">{{ uc.emoji }}</span>
                <span style="font-size:0.65rem;background:#0f172a;color:#94a3b8;
                             border-radius:4px;padding:0.15rem 0.4rem;font-weight:700;">{{ uc.id }}</span>
                <span [style.background]="uc.requiredType==='B2B' ? '#1e3a5f' :
                                          uc.requiredType==='B2C' ? '#4a1d96' : '#1c1917'"
                      [style.color]="uc.requiredType==='B2B' ? '#93c5fd' :
                                     uc.requiredType==='B2C' ? '#c4b5fd' : '#d6d3d1'"
                      style="font-size:0.6rem;border-radius:4px;padding:0.15rem 0.4rem;font-weight:700;">
                  {{ uc.requiredType }}
                </span>
              </div>
              <div style="font-size:0.9rem;font-weight:700;color:#f1f5f9;">{{ uc.title }}</div>
            </div>
          </div>

          <!-- Description -->
          <div style="font-size:0.78rem;color:#94a3b8;line-height:1.5;">{{ uc.description }}</div>

          <!-- Regulatory Ref -->
          <div *ngIf="uc.regulatoryRef"
               style="font-size:0.65rem;color:#475569;background:#0f172a;border-radius:4px;
                      padding:0.25rem 0.5rem;font-style:italic;">
            {{ uc.regulatoryRef }}
          </div>

          <!-- Test Data -->
          <div style="background:#0f172a;border-radius:8px;padding:0.75rem;">
            <div style="font-size:0.65rem;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;
                        margin-bottom:0.4rem;font-weight:600;">Testdaten</div>
            <div *ngFor="let d of uc.testData"
                 style="display:flex;justify-content:space-between;align-items:baseline;
                        margin-bottom:0.2rem;gap:0.5rem;">
              <span style="font-size:0.7rem;color:#64748b;flex-shrink:0;">{{ d.label }}</span>
              <span style="font-size:0.7rem;color:#22c55e;font-family:monospace;text-align:right;
                           word-break:break-all;">{{ resolveTestDataDisplay(d) }}</span>
            </div>
          </div>

          <!-- Steps -->
          <div>
            <div style="font-size:0.65rem;color:#64748b;text-transform:uppercase;letter-spacing:0.08em;
                        margin-bottom:0.4rem;font-weight:600;">Klickfolge</div>
            <ol style="margin:0;padding-left:1.2rem;">
              <li *ngFor="let step of uc.steps"
                  style="font-size:0.72rem;color:#94a3b8;margin-bottom:0.2rem;line-height:1.4;">
                {{ step }}
              </li>
            </ol>
          </div>

          <!-- Action Buttons -->
          <div style="display:flex;gap:0.5rem;margin-top:auto;flex-wrap:wrap;">
            <button (click)="directStart(uc)"
                    [disabled]="!currentUser || (uc.requiredType!=='ANY' && uc.requiredType!==currentUser?.type)"
                    style="flex:1;min-width:0;background:#2563eb;color:#fff;border:none;
                           border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;font-weight:600;
                           cursor:pointer;transition:opacity 0.2s;"
                    [style.opacity]="(!currentUser || (uc.requiredType!=='ANY' && uc.requiredType!==currentUser?.type)) ? '0.4' : '1'">
              🚀 Direkt-Start
            </button>

            <button *ngIf="uc.id==='UC-27'" (click)="openWebhookModal()"
                    style="flex:1;min-width:0;background:#0f172a;color:#60a5fa;border:1px solid #2563eb;
                           border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;font-weight:600;cursor:pointer;">
              📡 Webhook-Sim
            </button>

            <button *ngIf="uc.id==='UC-07'" (click)="downloadExport('camt053')"
                    style="flex:1;min-width:0;background:#0f172a;color:#34d399;border:1px solid #10b981;
                           border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;font-weight:600;cursor:pointer;">
              ⬇ CAMT.053
            </button>

            <button *ngIf="uc.id==='UC-08'" (click)="downloadExport('datev')"
                    style="flex:1;min-width:0;background:#0f172a;color:#34d399;border:1px solid #10b981;
                           border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;font-weight:600;cursor:pointer;">
              ⬇ DATEV CSV
            </button>

            <button *ngIf="uc.id==='UC-29a'" (click)="downloadExport('camt054')"
                    style="flex:1;min-width:0;background:#0f172a;color:#34d399;border:1px solid #10b981;
                           border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;font-weight:600;cursor:pointer;">
              ⬇ CAMT.054
            </button>

            <button *ngIf="uc.id==='UC-29b'" (click)="downloadExport('camt029')"
                    style="flex:1;min-width:0;background:#0f172a;color:#34d399;border:1px solid #10b981;
                           border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;font-weight:600;cursor:pointer;">
              ⬇ CAMT.029
            </button>

            <button *ngIf="uc.id==='UC-22'" (click)="runSanctionsScan()"
                    style="flex:1;min-width:0;background:#450a0a;color:#fca5a5;border:1px solid #dc2626;
                           border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;font-weight:600;cursor:pointer;">
              🔍 Scan starten
            </button>
          </div>

          <!-- Status messages -->
          <div *ngIf="ucMessages[uc.id]"
               [style.color]="ucMessages[uc.id].ok ? '#86efac' : '#fca5a5'"
               style="font-size:0.72rem;padding:0.4rem 0.6rem;border-radius:6px;background:#0f172a;">
            {{ ucMessages[uc.id].text }}
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div *ngIf="filteredEntries.length===0"
           style="text-align:center;padding:3rem;color:#475569;font-size:0.875rem;">
        Keine Use Cases für "{{ ucFilter }}" gefunden.
      </div>
    </section>

  </div><!-- /max-width container -->

  <!-- ── WEBHOOK SIMULATOR MODAL ───────────────────────────────────────────── -->
  <div *ngIf="showWebhookModal"
       style="position:fixed;inset:0;background:rgba(0,0,0,0.8);display:flex;
              align-items:center;justify-content:center;z-index:1000;padding:1rem;">
    <div style="background:#1e293b;border:1px solid #334155;border-radius:16px;
                padding:1.5rem;width:100%;max-width:520px;max-height:90vh;overflow-y:auto;">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;">
        <h3 style="margin:0;color:#f1f5f9;font-size:1rem;">📡 Inbound Webhook-Simulator (UC-27)</h3>
        <button (click)="showWebhookModal=false"
                style="background:transparent;border:none;color:#94a3b8;font-size:1.2rem;cursor:pointer;">✕</button>
      </div>

      <div style="display:flex;flex-direction:column;gap:0.75rem;">
        <div>
          <label style="display:block;font-size:0.75rem;color:#94a3b8;margin-bottom:0.3rem;">Empfänger-Wallet (walletId)</label>
          <input [(ngModel)]="webhook.walletId" style="width:100%;background:#0f172a;color:#e2e8f0;
                 border:1px solid #475569;border-radius:8px;padding:0.5rem 0.75rem;font-size:0.8rem;box-sizing:border-box;" />
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.75rem;">
          <div>
            <label style="display:block;font-size:0.75rem;color:#94a3b8;margin-bottom:0.3rem;">Betrag</label>
            <input [(ngModel)]="webhook.amount" type="number" style="width:100%;background:#0f172a;color:#e2e8f0;
                   border:1px solid #475569;border-radius:8px;padding:0.5rem 0.75rem;font-size:0.8rem;box-sizing:border-box;" />
          </div>
          <div>
            <label style="display:block;font-size:0.75rem;color:#94a3b8;margin-bottom:0.3rem;">Währung</label>
            <select [(ngModel)]="webhook.currency" style="width:100%;background:#0f172a;color:#e2e8f0;
                    border:1px solid #475569;border-radius:8px;padding:0.5rem 0.75rem;font-size:0.8rem;">
              <option>USDC</option><option>EURC</option>
            </select>
          </div>
        </div>
        <div>
          <label style="display:block;font-size:0.75rem;color:#94a3b8;margin-bottom:0.3rem;">Sender-Wallet (AML-Quelle)</label>
          <select [(ngModel)]="webhook.senderWallet" style="width:100%;background:#0f172a;color:#e2e8f0;
                  border:1px solid #475569;border-radius:8px;padding:0.5rem 0.75rem;font-size:0.8rem;">
            <option value="0xA100000000000000000000000000000000000001">✅ LOW_RISK — 0xA100...0001</option>
            <option value="0xDEAD000000000000000000000000000000000000">🚫 HIGH_RISK (AML-Block) — 0xDEAD...0000</option>
            <option value="0xUNKNOWN0000000000000000000000000000001">❓ UNBEKANNT (→ Sammelkonto)</option>
          </select>
        </div>
        <div>
          <label style="display:block;font-size:0.75rem;color:#94a3b8;margin-bottom:0.3rem;">Blockchain-Hash (eindeutig)</label>
          <div style="display:flex;gap:0.5rem;">
            <input [(ngModel)]="webhook.blockchainHash" style="flex:1;background:#0f172a;color:#e2e8f0;
                   border:1px solid #475569;border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;box-sizing:border-box;" />
            <button (click)="generateHash()" style="background:#0f172a;color:#60a5fa;border:1px solid #2563eb;
                    border-radius:8px;padding:0.5rem 0.75rem;font-size:0.75rem;cursor:pointer;white-space:nowrap;">
              🎲 Neu
            </button>
          </div>
        </div>

        <button (click)="triggerWebhook()" [disabled]="webhookLoading"
                style="background:#2563eb;color:#fff;border:none;border-radius:8px;
                       padding:0.75rem;font-size:0.875rem;font-weight:600;cursor:pointer;width:100%;">
          {{ webhookLoading ? '⏳ Sende...' : '📡 Webhook senden (POST /inbound/webhook)' }}
        </button>

        <div *ngIf="webhookResult"
             [style.background]="webhookResult.ok ? '#052e16' : '#450a0a'"
             [style.border]="webhookResult.ok ? '1px solid #16a34a' : '1px solid #dc2626'"
             style="border-radius:8px;padding:0.75rem;font-size:0.8rem;font-family:monospace;white-space:pre-wrap;word-break:break-all;"
             [style.color]="webhookResult.ok ? '#86efac' : '#fca5a5'">
          {{ webhookResult.text }}
        </div>
      </div>
    </div>
  </div>

</div>

<!-- ╔══════════════════════════════════════════════════════════════════╗ -->
<!-- ║   ATRUVIA STABLECOIN COPILOT — Floating Chat Widget             ║ -->
<!-- ╚══════════════════════════════════════════════════════════════════╝ -->

<!-- Floating Chat Button -->
<button (click)="toggleChat()"
  style="position:fixed;bottom:28px;right:28px;z-index:1000;
         width:56px;height:56px;border-radius:50%;border:none;cursor:pointer;
         background:linear-gradient(135deg,#1e40af,#0ea5e9);
         box-shadow:0 4px 20px rgba(14,165,233,.45);
         display:flex;align-items:center;justify-content:center;
         transition:transform .2s,box-shadow .2s;"
  [style.transform]="chatOpen ? 'scale(0.9)' : 'scale(1)'"
  title="Atruvia Stablecoin Copilot öffnen">
  <span style="font-size:22px;">{{chatOpen ? '✕' : '🤖'}}</span>
</button>

<!-- Chat Panel -->
<div *ngIf="chatOpen"
  style="position:fixed;bottom:96px;right:28px;z-index:999;
         width:390px;height:540px;border-radius:16px;overflow:hidden;
         background:#0f172a;border:1px solid #1e3a5f;
         box-shadow:0 12px 48px rgba(0,0,0,.65);
         display:flex;flex-direction:column;">

  <!-- Header -->
  <div style="background:linear-gradient(135deg,#1e3a5f,#0c2340);
              padding:14px 18px;display:flex;align-items:center;gap:12px;
              border-bottom:1px solid #1e3a5f;flex-shrink:0;">
    <div style="width:38px;height:38px;border-radius:50%;
                background:linear-gradient(135deg,#1e40af,#0ea5e9);
                display:flex;align-items:center;justify-content:center;font-size:18px;">🤖</div>
    <div>
      <div style="font-weight:700;font-size:14px;color:#f1f5f9;">Atruvia Stablecoin Copilot</div>
      <div style="font-size:11px;color:#22c55e;display:flex;align-items:center;gap:5px;">
        <span style="width:7px;height:7px;border-radius:50%;background:#22c55e;display:inline-block;
                     animation:copilotPulse 2s infinite;"></span>
        Online · Wissensdatenbank aktiv
      </div>
    </div>
    <div *ngIf="chatMessages.length > 0" style="margin-left:auto;">
      <button (click)="clearChat()"
        style="background:none;border:1px solid #1e3a5f;border-radius:6px;
               color:#64748b;font-size:10px;padding:3px 8px;cursor:pointer;">
        Leeren
      </button>
    </div>
  </div>

  <!-- Message Scroll Area -->
  <div #chatScrollArea id="chatScrollArea"
    style="flex:1;overflow-y:auto;padding:16px;display:flex;flex-direction:column;gap:12px;
           scrollbar-width:thin;scrollbar-color:#1e3a5f transparent;">

    <!-- Welcome & Quick Actions (initial) -->
    <ng-container *ngIf="chatMessages.length === 0">
      <div style="background:#1e293b;border-radius:12px;padding:14px 16px;
                  border:1px solid #1e3a5f;font-size:12.5px;color:#94a3b8;line-height:1.7;">
        👋 <strong style="color:#e2e8f0;">Willkommen beim Atruvia Stablecoin Copilot!</strong><br>
        Ich beantworte Fragen zur Plattform — fachlich, technisch, regulatorisch und architektonisch.
        Einfach tippen oder eine der Schnellfragen auswählen:
      </div>
      <div style="font-size:10.5px;color:#475569;font-weight:700;
                  letter-spacing:.08em;text-transform:uppercase;padding:2px 0;">
        Schnellstart-Fragen
      </div>
      <div *ngFor="let qa of quickActions; let i = index"
        (click)="sendMessage(qa.text)"
        style="background:#1e293b;border:1px solid #1e3a5f;border-radius:10px;
               padding:10px 13px;cursor:pointer;
               display:flex;align-items:flex-start;gap:10px;
               font-size:12px;color:#cbd5e1;line-height:1.5;
               transition:background .15s,border-color .15s;"
        onmouseover="this.style.background='#1e3a5f';this.style.borderColor='#3b82f6';"
        onmouseout="this.style.background='#1e293b';this.style.borderColor='#1e3a5f';">
        <span style="font-size:16px;flex-shrink:0;margin-top:1px;">{{qa.icon}}</span>
        <span>{{qa.text}}</span>
      </div>
    </ng-container>

    <!-- Conversation Messages -->
    <div *ngFor="let msg of chatMessages" style="display:flex;flex-direction:column;">

      <!-- User bubble -->
      <div *ngIf="msg.role === 'user'" style="display:flex;justify-content:flex-end;margin-bottom:2px;">
        <div style="max-width:82%;background:linear-gradient(135deg,#1e40af,#1d4ed8);
                    border-radius:14px 14px 3px 14px;padding:10px 14px;
                    font-size:12.5px;color:#e2e8f0;line-height:1.55;">
          {{msg.text}}
        </div>
      </div>

      <!-- Bot bubble -->
      <div *ngIf="msg.role === 'bot'" style="display:flex;flex-direction:column;gap:6px;max-width:94%;">
        <div style="background:#1e293b;border:1px solid #1e3a5f;
                    border-radius:3px 14px 14px 14px;padding:12px 14px;
                    font-size:12px;color:#cbd5e1;line-height:1.75;
                    white-space:pre-wrap;word-break:break-word;"
          [innerHTML]="renderMessage(msg.text)">
        </div>
        <div *ngIf="msg.sources && msg.sources.length"
          style="display:flex;flex-wrap:wrap;gap:5px;padding-left:2px;">
          <span *ngFor="let src of msg.sources"
            style="background:#0f2d4a;border:1px solid #1e3a5f;border-radius:5px;
                   padding:2px 8px;font-size:10px;color:#64748b;cursor:default;"
            [title]="src">{{src}}</span>
        </div>
      </div>
    </div>

    <!-- Typing indicator -->
    <div *ngIf="chatTyping"
      style="display:flex;align-items:center;gap:6px;padding:10px 14px;
             background:#1e293b;border:1px solid #1e3a5f;
             border-radius:3px 14px 14px 14px;width:fit-content;">
      <span style="font-size:11px;color:#64748b;">Copilot schreibt</span>
      <span style="width:6px;height:6px;border-radius:50%;background:#3b82f6;display:inline-block;
                   animation:dot 1.2s infinite .0s;"></span>
      <span style="width:6px;height:6px;border-radius:50%;background:#3b82f6;display:inline-block;
                   animation:dot 1.2s infinite .2s;"></span>
      <span style="width:6px;height:6px;border-radius:50%;background:#3b82f6;display:inline-block;
                   animation:dot 1.2s infinite .4s;"></span>
    </div>
  </div>

  <!-- Input Row -->
  <div style="padding:12px 14px;border-top:1px solid #1e3a5f;background:#0c1a2e;
              display:flex;gap:8px;align-items:flex-end;flex-shrink:0;">
    <textarea [(ngModel)]="chatInput"
      (keydown)="onChatKeydown($event)"
      placeholder="Frage stellen... (Enter = Senden)"
      rows="2"
      style="flex:1;background:#1e293b;border:1px solid #1e3a5f;border-radius:10px;
             padding:9px 12px;color:#e2e8f0;font-size:12.5px;resize:none;
             font-family:inherit;outline:none;line-height:1.5;
             scrollbar-width:thin;scrollbar-color:#1e3a5f transparent;">
    </textarea>
    <button (click)="sendMessage()"
      [disabled]="!chatInput.trim() || chatTyping"
      style="width:40px;height:40px;border-radius:10px;border:none;cursor:pointer;
             background:linear-gradient(135deg,#1e40af,#0ea5e9);
             color:#fff;font-size:18px;flex-shrink:0;line-height:1;
             transition:opacity .2s;"
      [style.opacity]="(!chatInput.trim() || chatTyping) ? '0.35' : '1'">
      ➤
    </button>
  </div>
</div>

<style>
  @keyframes copilotPulse { 0%,100%{opacity:1} 50%{opacity:.4} }
  @keyframes dot { 0%,80%,100%{transform:translateY(0)} 40%{transform:translateY(-5px)} }
</style>

  `
})
export class DevPortalComponent implements OnInit {

  private http = inject(HttpClient);
  private router = inject(Router);
  private txService = inject(TransactionService);

  // ── Auth State ──────────────────────────────────────────────────────────────
  currentUser: UserProfile | null = null;
  currentTenant: Tenant | null = null;
  selectedTenantId = 'tenant-kleine-vb';
  selectedUserId = 'cust-b2b-001';
  loginLoading = false;
  loginError = '';

  // ── System ──────────────────────────────────────────────────────────────────
  healthStatus = 'CHECKING';

  // ── Playbook UI ─────────────────────────────────────────────────────────────
  activeTab = 'b2b';
  ucFilter = '';
  hoveredUc = '';
  ucMessages: Record<string, { ok: boolean; text: string }> = {};
  totalUseCase = 0;
  missingUiCount = 11;

  // ── Webhook Modal ───────────────────────────────────────────────────────────
  showWebhookModal = false;
  webhookLoading = false;
  webhookResult: { ok: boolean; text: string } | null = null;
  webhook = {
    walletId: '0xA100000000000000000000000000000000000001',
    amount: 1000,
    currency: 'USDC',
    senderWallet: '0xA100000000000000000000000000000000000001',
    blockchainHash: '0x' + Math.random().toString(16).slice(2, 18) + Date.now().toString(16)
  };

  // ── Stammdaten ──────────────────────────────────────────────────────────────
  readonly tenants: Tenant[] = [
    { id: 'tenant-kleine-vb', name: 'Volksbank Kleinstadt eG', short: 'VB Klein', color: '#86efac', bg: '#052e16' },
    { id: 'tenant-grosse-vb', name: 'Volksbank Metropole eG', short: 'VB Metro', color: '#93c5fd', bg: '#1e3a5f' },
    { id: 'tenant-marktbank', name: 'Marktbank AG', short: 'Markt', color: '#c4b5fd', bg: '#4a1d96' }
  ];

  readonly users: UserProfile[] = [
    { customerId: 'cust-b2b-001',      name: 'Müller GmbH',         role: 'Initiator/Admin', iban: 'DE89370400440532090001', type: 'B2B', tenantId: 'tenant-kleine-vb' },
    { customerId: 'cust-b2b-approver', name: 'Schmidt AG',           role: 'Zweitfreigeber',  iban: 'DE89370400440532090002', type: 'B2B', tenantId: 'tenant-kleine-vb' },
    { customerId: 'cust-b2b-001',      name: 'Müller GmbH (Metro)',  role: 'Initiator',       iban: 'DE89370400440532090003', type: 'B2B', tenantId: 'tenant-grosse-vb' },
    { customerId: 'cust-b2c-001',      name: 'Max Mustermann',       role: 'Privatkunde',     iban: 'DE27200400600532090001', type: 'B2C', tenantId: 'tenant-kleine-vb' },
    { customerId: 'cust-b2c-001',      name: 'Mustermann (Metro)',   role: 'Privatkunde',     iban: 'DE27200400600532090003', type: 'B2C', tenantId: 'tenant-grosse-vb' }
  ];

  get filteredUsers(): UserProfile[] {
    return this.users.filter(u => u.tenantId === this.selectedTenantId);
  }

  // ── Playbook Data ───────────────────────────────────────────────────────────
  readonly tabs: Tab[] = [
    {
      id: 'b2b', label: 'B2B Outbound', emoji: '🏢',
      entries: [
        {
          id: 'UC-01', emoji: '💸', title: 'Standard-Überweisung (< 25k)', requiredType: 'B2B',
          description: 'Sofortige Stablecoin-Transaktion bis 25.000 EUR ohne Vier-Augen-Freigabe. Kernprozess für genossenschaftliche Zahlungsabwicklung.',
          regulatoryRef: 'MiCA Art. 23 · PSD2 · ZAG §17',
          testData: [
            { label: 'Quell-IBAN', value: 'DE89370400440532013000' },
            { label: 'Ziel-Wallet', value: '0xA100000000000000000000000000000000000001' },
            { label: 'Betrag', value: '1.000 EUR' },
            { label: 'Währung', value: 'USDC' }
          ],
          steps: [
            'Mandant "Volksbank Kleinstadt" + Müller GmbH einloggen',
            'Menü → "Neue Überweisung"',
            'IBAN + Ziel-Wallet eintragen, Betrag: 1.000 EUR',
            'Absenden → Status wechselt auf SUBMITTED → SETTLED'
          ],
          targetRoute: '/b2b/new-transfer'
        },
        {
          id: 'UC-02', emoji: '✋', title: 'Großbetrag mit Vier-Augen (> 25k)', requiredType: 'B2B',
          description: 'Überweisungen ab 25.001 EUR erfordern Zweitzgenehmigung. Initiator und Approver müssen verschiedene Nutzer sein (Self-Approval-Block).',
          regulatoryRef: 'FATF Rec. 16 · §25a KWG',
          testData: [
            { label: 'Betrag', value: '50.000 EUR' },
            { label: 'Ziel-Wallet', value: '0xA100000000000000000000000000000000000001' },
            { label: 'Approver', value: 'Schmidt AG (separater Login)' }
          ],
          steps: [
            'Als Müller GmbH einloggen, Transfer > 25.000 EUR senden',
            'Status: PENDING_APPROVAL',
            'In neuem Tab: Als Schmidt AG (Approver) einloggen',
            'Menü → "Freigaben" → Transfer genehmigen',
            'Status: APPROVED → SETTLED'
          ],
          targetRoute: '/b2b/new-transfer'
        },
        {
          id: 'UC-04', emoji: '💱', title: 'Rate Quote (FX-Kursgarantie 60s)', requiredType: 'B2B',
          description: 'EZB-Referenzkurs + 0,15% Spread. Kurs wird 60 Sekunden garantiert. Slippage-Schutz: max. 100 BPS Abweichung.',
          regulatoryRef: 'MiCA Art. 23 · G-06 Slippage',
          testData: [
            { label: 'Betrag', value: '10.000 EUR' },
            { label: 'Zielwährung', value: 'USDC' },
            { label: 'Spread', value: '0,15% (mandantenspezifisch)' }
          ],
          steps: [
            '"Neue Überweisung" → "Rate Quote anfordern"',
            'Kurs wird 60s gesperrt → bestätigen',
            'Transfer mit garantiertem Kurs ausführen'
          ],
          targetRoute: '/b2b/new-transfer'
        },
        {
          id: 'UC-05', emoji: '📋', title: 'Adressbuch (Whitelist-Verwaltung)', requiredType: 'B2B',
          description: 'Nur whitelisted Wallets können als Ziel verwendet werden (MiCA/FATF). Jede Adresse wird AML-gescreened.',
          testData: [
            { label: 'Wallet', value: '0xA100...0001 (LOW RISK)' },
            { label: 'Label', value: 'Mustermann GmbH' },
            { label: 'Währung', value: 'USDC' }
          ],
          steps: [
            'Menü → "Adressbuch"',
            '"Neue Adresse" → Wallet + Label eintragen',
            'Chainalysis-Screening läuft → Status: ACTIVE oder BLOCKED'
          ],
          targetRoute: '/b2b/address-book'
        },
        {
          id: 'UC-06', emoji: '🚫', title: 'Compliance-Block (AML)', requiredType: 'B2B',
          description: 'Überweisung an DEAD-Wallet wird durch Chainalysis automatisch blockiert. TX bleibt FAILED, Hold wird freigegeben.',
          testData: [
            { label: 'Test-Wallet BLOCKED', value: '0xDEAD000000000000000000000000000000000000' },
            { label: 'Risiko-Score', value: 'HIGH (SANCTIONS)' },
            { label: 'Ergebnis', value: 'TX → FAILED' }
          ],
          steps: [
            '"Neue Überweisung" → DEAD-Wallet als Ziel (muss vorher gewhitelistet sein)',
            'Alternativ: Direkt-Start-Button triggers API-Call',
            'AML-Block → ComplianceBlockException → Status: FAILED'
          ],
          targetRoute: '/b2b/new-transfer'
        },
        {
          id: 'UC-07', emoji: '📄', title: 'CAMT.053 Export (Kontoauszug)', requiredType: 'ANY',
          description: 'ISO 20022 CAMT.053.001.08 XML für alle SETTLED Transaktionen. Für ERP-Systeme (SAP) und DATEV-Integration. Download via Button.',
          regulatoryRef: 'ISO 20022 · HGB §238 Aufbewahrungspflicht',
          testData: [
            { label: 'Format', value: 'CAMT.053.001.08 XML' },
            { label: 'Filter', value: 'Status=SETTLED' },
            { label: 'Endpunkt', value: 'GET /api/v1/b2b/export/camt053' }
          ],
          steps: [
            'Direkt-Start → Download über API',
            'Oder: Button "⬇ CAMT.053" rechts unten im UC-Card'
          ],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'UC-08', emoji: '📊', title: 'DATEV-Export (Steuer-CSV)', requiredType: 'ANY',
          description: 'DATEV-kompatibles CSV mit allen Buchungsdaten. Für Steuerberater und Jahresabschluss. Download direkt über UI-Button.',
          testData: [
            { label: 'Format', value: 'CSV (Datum,Belegnr,Betrag_EUR,...)' },
            { label: 'Endpunkt', value: 'GET /api/v1/b2b/export/datev' }
          ],
          steps: ['Button "⬇ DATEV CSV" im UC-Card klicken'],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'UC-22', emoji: '🔍', title: 'Nachtl. Sanctions-Batch', requiredType: 'ANY',
          description: 'Nachtlicher Batch prüft alle aktiven Wallet-Adressen gegen OFAC + EU Consolidated Sanctions List. Revoked Adressen werden n8n-Alert ausgelöst.',
          regulatoryRef: 'OFAC · EU 2580/2001 · §25b KWG',
          testData: [
            { label: 'Trigger', value: 'POST /api/v1/b2b/admin/sanctions-scan' },
            { label: 'Normal', value: 'Automatisch 02:00 Uhr' }
          ],
          steps: [
            '"🔍 Scan starten" Button klicken',
            'Batch läuft asynchron; hochriskante Adressen werden widerrufen',
            'n8n-Alert bei neuen Treffern'
          ],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'UC-24', emoji: '📦', title: 'Bulk-Payment (CSV-Import)', requiredType: 'B2B',
          description: 'Bis zu 1000 Zeilen CSV-Upload. Pro Zeile: destinationWallet,amountEur,currency,reference. Mindest-Erfolgsquote konfigurierbar per Mandant (G-13).',
          regulatoryRef: 'PSD2 · G-13 Bulk-Quota',
          testData: [
            { label: 'CSV-Format', value: 'destinationWallet,amountEur,currency,ref' },
            { label: 'Beispielzeile', value: '0xA100...,500,USDC,Rechnung-001' },
            { label: 'Max. Zeilen', value: '1000 (TenantSettings)' }
          ],
          steps: [
            'API: POST /api/v1/b2b/bulk-payments (multipart CSV)',
            'Kein UI-Widget vorhanden → Direkt-Start zeigt API-Payload'
          ],
          targetRoute: '/b2b/transfers'
        }
      ]
    },
    {
      id: 'b2c', label: 'B2C Retail', emoji: '👤',
      entries: [
        {
          id: 'UC-11', emoji: '🌍', title: 'Remittance (Auslandsüberweisung)', requiredType: 'B2C',
          description: 'Günstige Auslandsüberweisung via Stablecoin. Ideal für Migranten: Geld in Sekunden, nicht Tagen — zu einem Bruchteil der SWIFT-Kosten.',
          testData: [
            { label: 'Quelle', value: 'DE27200400600532013001' },
            { label: 'Betrag', value: '500 EUR' },
            { label: 'Währung', value: 'EURC' }
          ],
          steps: ['Als Max Mustermann einloggen', 'Menü → "Remittances"', 'Betrag + Zielwallet eintragen'],
          targetRoute: '/b2c/remittances'
        },
        {
          id: 'UC-12', emoji: '📱', title: 'P2P per Telefonnummer', requiredType: 'B2C',
          description: 'Geld senden an Handynummer. Empfänger muss Telefonnummer registriert haben. Hash: HMAC-SHA256 (G-14 DSGVO-Fix).',
          regulatoryRef: 'DSGVO Art. 32 · G-14',
          testData: [
            { label: 'Sender-IBAN', value: 'DE27200400600532013001' },
            { label: 'Empfänger', value: '+49 151 12345678 (muss registriert sein)' },
            { label: 'Betrag', value: '50 EUR' }
          ],
          steps: ['Als Max Mustermann einloggen', 'Menü → "P2P"', 'Handynummer + Betrag eingeben'],
          targetRoute: '/b2c/p2p'
        },
        {
          id: 'UC-16', emoji: '💰', title: 'Yield-Sparkonto eröffnen', requiredType: 'B2C',
          description: '3,5% p.a. Zinsen auf EURC-Guthaben (RWA Money Market Fund). Zinseszins täglich. Jahresabschluss-Bewertung via AtruviaTaxClient (G-15).',
          regulatoryRef: 'EStG §43 · G-15 Jahresabschluss',
          testData: [
            { label: 'Mindestbetrag', value: '100 EUR' },
            { label: 'Zinssatz', value: '3,5% p.a. (Zinseszins täglich)' },
            { label: 'Steuer', value: 'Via Atruvia Tax Engine (Drittsystem)' }
          ],
          steps: ['Als Max Mustermann einloggen', 'Menü → "Yield-Sparkonto"', '"Einlage" → Betrag eingeben'],
          targetRoute: '/b2c/yield'
        },
        {
          id: 'UC-17', emoji: '💳', title: 'Yield-Position auflösen', requiredType: 'B2C',
          description: 'Auszahlung von Kapital + Zinsen. Steuer wird automatisch an Atruvia Tax Engine (Drittsystem) gemeldet — KapErSt + SoliZ unter Berücksichtigung des Freistellungsauftrags.',
          testData: [
            { label: 'FSA', value: '1.000 EUR (via Drittsystem)' },
            { label: 'Netto-Auszahlung', value: 'nach Steuerabzug auf IBAN' }
          ],
          steps: ['Bestehende Yield-Position aufrufen', '"Auflösen" klicken', 'Netto-Auszahlung auf Konto gutgeschrieben'],
          targetRoute: '/b2c/yield'
        }
      ]
    },
    {
      id: 'compliance', label: 'Compliance & Admin', emoji: '⚖️',
      entries: [
        {
          id: 'UC-27', emoji: '📡', title: 'Inbound Webhook (Stablecoin-Eingang)', requiredType: 'ANY',
          description: 'Simuliert einen eingehenden Circle/Taurus-Webhook. Startet Post-Receive AML-Screening + Core-Banking-Gutschrift. HMAC-Signatur in Prod erforderlich (G-14).',
          regulatoryRef: 'UC-27 · G-14 Webhook-Signatur',
          testData: [
            { label: 'Empfänger-Wallet', value: '0xBankB2BWallet000000000000000000000000001' },
            { label: 'LOW_RISK Sender', value: '0xA100...0001 → SETTLED' },
            { label: 'HIGH_RISK Sender', value: '0xDEAD...0000 → FAILED + AML' },
            { label: 'UNBEKANNT Wallet', value: '0xUNKNOWN... → Sammelkonto' }
          ],
          steps: [
            '"📡 Webhook-Sim" Button öffnet Modal',
            'Sender-Wallet wählen (LOW/HIGH/UNBEKANNT)',
            'Webhook senden → Response zeigt TX-Status'
          ],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'UC-30', emoji: '↩️', title: 'Automatische Retoure (R-Transaktion)', requiredType: 'ANY',
          description: 'Inbound-Zahlung auf SUSPENDED/BLOCKED-Konto wird automatisch an Absender-Wallet zurückgesendet. parentTransactionId verknüpft Retoure mit Original.',
          regulatoryRef: 'SEPA R-Transaktionen · UC-30',
          testData: [
            { label: 'Trigger', value: 'Konto-Status = SUSPENDED oder BLOCKED' },
            { label: 'INBOUND_RETURN TX', value: 'parentTransactionId → Original TX' },
            { label: 'Simulation', value: 'Webhook-Sim → Konto vorher sperren' }
          ],
          steps: [
            'Kundenkonto auf SUSPENDED setzen (Admin-API)',
            'Inbound-Webhook mit Ziel-Wallet senden (Webhook-Simulator)',
            'Ergebnis: Original FAILED + INBOUND_RETURN RETURNED'
          ],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'UC-31', emoji: '🗳️', title: 'Sammelkonto (Unzuordenbare Eingänge)', requiredType: 'ANY',
          description: 'Unbekannte Wallet-Adresse → TX landet auf Sammelkonto (customer_id=unassigned-funds). Sachbearbeiter ordnet manuell zu.',
          testData: [
            { label: 'Unbekannte Wallet', value: '0xCC00000000000000000000000000000000000001' },
            { label: 'Sammelkonto-IBAN', value: 'SYSTEM-COLLECTION-0000000000000000' },
            { label: 'Admin-Reassign', value: 'POST /admin/reassign-transaction' }
          ],
          steps: [
            'Webhook-Sim: Unbekannte Wallet als walletId wählen',
            'TX erscheint auf Sammelkonto (Status: UNASSIGNED)',
            'POST /admin/reassign-transaction mit Ziel-IBAN'
          ],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'UC-28', emoji: '🔐', title: 'Multi-Tenancy RLS-Isolation', requiredType: 'ANY',
          description: 'PostgreSQL RLS sorgt für vollständige Datentrennung zwischen Mandanten. JWT tenant-Claim → set_config → RLS-Policy. Kein Cross-Tenant-Zugriff möglich.',
          regulatoryRef: 'DSGVO · GenG §22',
          testData: [
            { label: 'Tenant A', value: 'tenant-kleine-vb (Volksbank Kleinstadt)' },
            { label: 'Tenant B', value: 'tenant-grosse-vb (Volksbank Metropole)' },
            { label: 'Test', value: 'Tenant B sieht KEINE TX von Tenant A' }
          ],
          steps: [
            'Als Tenant A (VB Kleinstadt) einloggen, TX erstellen',
            'Abmelden → Als Tenant B (VB Metropole) einloggen',
            'TX-Liste prüfen: TX von Tenant A ist NICHT sichtbar ✓'
          ],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'G-07', emoji: '🚨', title: 'Emergency Kill Switch (G-07)', requiredType: 'ANY',
          description: 'Globaler Emergency-Stop: blockiert alle POST/PUT/DELETE-Requests. DORA Art. 17 + §25a KWG. Mandanten- oder systemweite Aktivierung.',
          regulatoryRef: 'DORA Art. 17 · §25a KWG · G-07',
          testData: [
            { label: 'Aktivieren', value: 'POST /admin/kill-switch/activate {scope:"GLOBAL"}' },
            { label: 'Deaktivieren', value: 'POST /admin/kill-switch/deactivate {scope:"GLOBAL"}' },
            { label: 'Resultat', value: 'HTTP 503 SYSTEM_003 bei Schreib-Requests' }
          ],
          steps: [
            'Direkt-Start → Kill Switch aktivieren (GLOBAL)',
            'Transfer-Versuch → HTTP 503 SYSTEM_003',
            'Kill Switch deaktivieren → normaler Betrieb'
          ],
          targetRoute: '/b2b/transfers'
        }
      ]
    },
    {
      id: 'exports', label: 'Exporte', emoji: '📤',
      entries: [
        {
          id: 'UC-29a', emoji: '📩', title: 'CAMT.054 Echtzeit-Avisierung', requiredType: 'ANY',
          description: 'ISO 20022 CAMT.054.001.08 — Bank-to-Customer Debit Credit Notification. ERP-Systeme (SAP) verbuchen Stablecoin-Eingänge in Echtzeit.',
          regulatoryRef: 'ISO 20022 · UC-29 · MiCA Art. 23',
          testData: [
            { label: 'Format', value: 'CAMT.054.001.08 XML' },
            { label: 'Filter', value: 'type=INBOUND, status=SETTLED' },
            { label: 'CdtDbtInd', value: 'CRDT · BkTxCd: PMNT/RCDT/ESCT' }
          ],
          steps: ['"⬇ CAMT.054" Button klicken → XML-Download'],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'UC-29b', emoji: '📨', title: 'CAMT.029 Rejection-Nachricht', requiredType: 'ANY',
          description: 'ISO 20022 CAMT.029.001.09 — Unable To Apply Notification für automatische Retouren (INBOUND_RETURN). ERP-Systeme verbuchen Fehlschläge.',
          regulatoryRef: 'ISO 20022 · UC-30 · G-10',
          testData: [
            { label: 'Format', value: 'CAMT.029.001.09 XML' },
            { label: 'Filter', value: 'type=INBOUND_RETURN, letzte 24h' },
            { label: 'CxlStsRsn', value: 'AC04 (Account Not Active)' }
          ],
          steps: ['"⬇ CAMT.029" Button klicken → XML-Download (letzte 24h)'],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'G-04', emoji: '🔄', title: 'EOD Reconciliation (G-04)', requiredType: 'ANY',
          description: 'Täglicher Soll/Haben-Abgleich zwischen Fiat-Ledger und On-Chain-Salden. AT 7.2 MaRisk. Läuft automatisch 23:00 Uhr pro Mandant.',
          regulatoryRef: 'AT 7.2 MaRisk · §25a KWG · HGB §238',
          testData: [
            { label: 'Trigger', value: '@Scheduled("0 0 23 * * ?")' },
            { label: 'Status', value: 'BALANCED | DISCREPANCY | ERROR' },
            { label: 'Schwelle', value: '0,01 EUR Toleranz' }
          ],
          steps: [
            'Automatisch täglich 23:00 Uhr',
            'Ergebnis in reconciliation_run Tabelle',
            'Bei DISCREPANCY: n8n-Alert'
          ],
          targetRoute: '/b2b/transfers'
        }
      ]
    },
    {
      id: 'bafin', label: 'BaFin Compliance', emoji: '🏛️',
      entries: [
        {
          id: 'G-01', emoji: '💰', title: 'G-01 Buchungskreislauf (Gross-Debit)', requiredType: 'B2B',
          description: 'Brutto-Modell: Hold + LedgerBooking auf amountFiat + Fee + Spread. Bei Circle-Failure nach Ledger-Commit: automatische Storno-Buchung (reverseBooking).',
          regulatoryRef: 'MiCA Art. 23 · HGB §246 · G-01',
          testData: [
            { label: 'Gross Debit', value: 'amountFiat + flatFee + spreadAmount' },
            { label: 'Transit-Konto', value: 'DE00ATRUVIA0001TRANSIT' },
            { label: 'Ertrags-Konto', value: 'DE00ATRUVIA0001ERTRAG' },
            { label: 'Storno-Ref', value: 'ledger_booking_reference' }
          ],
          steps: [
            'Standard-Transfer initiieren (UC-01)',
            'Im Transfer-Detail: grossDebit > amountFiat (Gebühr sichtbar)',
            'Bei Fehler nach Ledger: Storno automatisch via reverseBooking()'
          ],
          targetRoute: '/b2b/new-transfer'
        },
        {
          id: 'G-03', emoji: '⚙️', title: 'G-03 TenantSettings (Mandanten-Preise)', requiredType: 'ANY',
          description: 'Jede Volksbank setzt eigene Preise, Limits und Spreads. Mandantenspezifische Konfiguration in tenant_settings-Tabelle.',
          regulatoryRef: 'GenG §1/§22 · G-03',
          testData: [
            { label: 'VB Kleinstadt FX-Spread', value: '0,15% (Default)' },
            { label: 'Approval-Schwelle B2B', value: '25.000 EUR (Default)' },
            { label: 'Slippage-Toleranz', value: '100 BPS = 1% (Default)' }
          ],
          steps: [
            'Backend: tenant_settings Tabelle zeigt Mandanten-Konditionen',
            'Jeder Mandant kann individuell angepasst werden',
            'Änderungen im Admin: POST /admin/tenant-settings'
          ],
          targetRoute: '/b2b/transfers'
        },
        {
          id: 'G-12', emoji: '🌐', title: 'G-12 Travel Rule (FATF Rec. 16)', requiredType: 'B2B',
          description: 'Cross-Border-Transfers ab travel_rule_threshold (Default: 15.000 EUR) erfordern vollständige Begünstigtendaten (Name, Adresse, Konto-ID).',
          regulatoryRef: 'FATF Rec. 16 · MiCA Art. 83 · G-12',
          testData: [
            { label: 'Schwelle', value: '15.000 EUR (konfigurierbar)' },
            { label: 'Pflichtfelder', value: 'beneficiaryName + beneficiaryAddress' },
            { label: 'Fehler-Code', value: 'FATF_001 (HTTP 400)' }
          ],
          steps: [
            'Transfer > 15.000 EUR initiieren',
            'Ohne Begünstigtendaten → FATF_001-Fehler',
            'Mit Name + Adresse → Transfer wird verarbeitet'
          ],
          targetRoute: '/b2b/new-transfer'
        },
        {
          id: 'G-14', emoji: '🔒', title: 'G-14 Telefonnummer-Hash (DSGVO)', requiredType: 'B2C',
          description: 'P2P-Telefonnummern werden mit HMAC-SHA256 + serverseitigem Schlüssel (PHONE_HMAC_KEY) gehasht — nicht mehr mit statischem Salt im Quellcode.',
          regulatoryRef: 'DSGVO Art. 32 · §25 BDSG · G-14',
          testData: [
            { label: 'Algorithmus', value: 'HMAC-SHA256 (deterministisch)' },
            { label: 'Key', value: 'PHONE_HMAC_KEY Env-Variable (Prod!)' },
            { label: 'Prod-Aktion', value: 'PHONE_HMAC_KEY setzen!' }
          ],
          steps: [
            'P2P-Telefonnummer registrieren (UC-12)',
            'Backend: hash = HMAC-SHA256(PHONE_HMAC_KEY, normalized_phone)',
            'phone_alias.phone_hash_algorithm = "HMAC_SHA256_V1"'
          ],
          targetRoute: '/b2c/p2p'
        }
      ]
    }
  ];

  // ── Copilot Chat State ────────────────────────────────────────────────────────

  chatOpen   = false;
  chatInput  = '';
  chatTyping = false;
  chatMessages: { role: 'user' | 'bot'; text: string; sources?: string[] }[] = [];

  quickActions = [
    { icon: '💡', text: 'Wie sichern wir die Datenisolation (Multi-Tenancy) zwischen den VR-Banken ab?' },
    { icon: '🔒', text: 'Was passiert gesetzlich und technisch bei einem Sanktionstreffer?' },
    { icon: '📈', text: 'Wie ist das Yield-Guthaben und die Zinsberechnung steuerrechtlich (DATEV) gelöst?' },
    { icon: '⚙️', text: 'Wie schützt das Outbox Pattern uns vor Systemabstürzen?' }
  ];

  toggleChat() { this.chatOpen = !this.chatOpen; }
  clearChat()  { this.chatMessages = []; }

  onChatKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  sendMessage(text?: string) {
    const msg = (text ?? this.chatInput).trim();
    if (!msg || this.chatTyping) return;
    this.chatInput = '';
    this.chatMessages.push({ role: 'user', text: msg });
    this.chatTyping = true;
    this.scrollChat();

    const tenantId = localStorage.getItem('tenant_id') ?? 'tenant-default';
    this.http.post<{ reply: string; sourceReferences: string[] }>(
      '/api/v1/common/dev-chat',
      { message: msg, currentTenantId: tenantId }
    ).subscribe({
      next: res => {
        setTimeout(() => {
          this.chatTyping = false;
          this.chatMessages.push({
            role: 'bot',
            text: res.reply,
            sources: res.sourceReferences ?? []
          });
          this.scrollChat();
        }, 850);
      },
      error: () => {
        this.chatTyping = false;
        this.chatMessages.push({
          role: 'bot',
          text: '⚠️ Der Copilot ist momentan nicht verfügbar. Bitte prüfe, ob das Backend läuft und das dev-Profil aktiv ist.'
        });
        this.scrollChat();
      }
    });
  }

  renderMessage(text: string): string {
    // Minimal Markdown → HTML (Bold, Code, Listeneinträge, Zeilenumbrüche)
    return text
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\*\*(.+?)\*\*/g, '<strong style="color:#e2e8f0;">$1</strong>')
      .replace(/`(.+?)`/g, '<code style="background:#0f172a;padding:1px 5px;border-radius:3px;font-family:monospace;color:#7dd3fc;">$1</code>')
      .replace(/^[-•]\s(.+)$/gm, '<span style="display:block;padding-left:12px;position:relative;"><span style="position:absolute;left:0;color:#3b82f6;">▸</span>$1</span>')
      .replace(/\n/g, '<br>');
  }

  private scrollChat() {
    setTimeout(() => {
      const el = document.getElementById('chatScrollArea');
      if (el) el.scrollTop = el.scrollHeight;
    }, 50);
  }

  // ── Lifecycle ─────────────────────────────────────────────────────────────────

  ngOnInit() {
    this.totalUseCase = this.tabs.reduce((sum, t) => sum + t.entries.length, 0);
    this.checkHealth();
    this.restoreSession();
    setInterval(() => this.checkHealth(), 30000);
  }

  // ── Computed ───────────────────────────────────────────────────────────────────

  get filteredEntries(): PlaybookEntry[] {
    const activeTabEntries = this.tabs.find(t => t.id === this.activeTab)?.entries ?? [];
    if (!this.ucFilter.trim()) return activeTabEntries;
    const q = this.ucFilter.toLowerCase();
    return activeTabEntries.filter(e =>
      e.id.toLowerCase().includes(q) ||
      e.title.toLowerCase().includes(q) ||
      e.description.toLowerCase().includes(q)
    );
  }

  // ── Health Check ───────────────────────────────────────────────────────────────

  checkHealth() {
    this.http.get<{ status: string }>('/actuator/health').subscribe({
      next: r => this.healthStatus = r.status,
      error: () => this.healthStatus = 'DOWN'
    });
  }

  // ── Auth ───────────────────────────────────────────────────────────────────────

  onTenantChange() {
    const firstUser = this.filteredUsers[0];
    if (firstUser) this.selectedUserId = firstUser.customerId;
  }

  login() {
    this.loginLoading = true;
    this.loginError = '';
    const tenant = this.selectedTenantId;
    const userId = this.selectedUserId;

    this.http.get<{ token: string }>(
      `/api/v1/auth/dev-token?customerId=${userId}&tenant=${tenant}`
    ).subscribe({
      next: res => {
        localStorage.setItem('access_token', res.token);
        localStorage.setItem('tenant_id', tenant);
        this.currentTenant = this.tenants.find(t => t.id === tenant) ?? null;
        this.currentUser = this.filteredUsers.find(u => u.customerId === userId) ?? null;
        this.loginLoading = false;
      },
      error: err => {
        this.loginError = err?.error?.message ?? 'Login fehlgeschlagen — Backend erreichbar?';
        this.loginLoading = false;
      }
    });
  }

  logout() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('tenant_id');
    sessionStorage.removeItem('playbook_prefill');
    this.currentUser = null;
    this.currentTenant = null;
  }

  restoreSession() {
    const token = localStorage.getItem('access_token');
    const tenantId = localStorage.getItem('tenant_id') ?? 'tenant-kleine-vb';
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const customerId = payload.sub;
        this.selectedTenantId = tenantId;
        this.currentTenant = this.tenants.find(t => t.id === tenantId) ?? null;
        this.currentUser = this.users.find(u => u.customerId === customerId && u.tenantId === tenantId)
                        ?? this.users.find(u => u.customerId === customerId) ?? null;
      } catch { /* ignore */ }
    }
  }

  goToDashboard() {
    if (!this.currentUser) return;
    this.router.navigate([this.currentUser.type === 'B2C' ? '/b2c/remittances' : '/b2b/transfers']);
  }

  // ── Direkt-Start ──────────────────────────────────────────────────────────────

  /** Zeigt in der Karte dynamisch die IBAN des eingeloggten Nutzers statt eines Platzhalters. */
  resolveTestDataDisplay(d: TestDatum): string {
    const ibanLabels = ['Quell-IBAN', 'Quelle', 'Sender-IBAN', 'IBAN'];
    if (ibanLabels.includes(d.label) && this.currentUser?.iban) {
      return this.currentUser.iban;
    }
    return d.value;
  }

  directStart(uc: PlaybookEntry) {
    if (!this.currentUser) {
      this.login();
      return;
    }

    // IBAN-Platzhalter durch die tatsächliche IBAN des eingeloggten Nutzers ersetzen.
    // Hintergrund: Die testData-Einträge enthalten generische Demo-IBANs;
    // der eingeloggte Tenant hat aber mandantenspezifische IBANs aus V24-Seed.
    const ibanLabels = ['Quell-IBAN', 'Quelle', 'Sender-IBAN', 'IBAN'];
    const userIban = this.currentUser.iban;
    const resolvedTestData = uc.testData.map(d =>
      (ibanLabels.includes(d.label) && userIban) ? { ...d, value: userIban } : d
    );

    // Prefill-Daten in sessionStorage speichern
    const prefill = {
      ucId: uc.id,
      testData: Object.fromEntries(resolvedTestData.map(d => [d.label, d.value]))
    };
    sessionStorage.setItem('playbook_prefill', JSON.stringify(prefill));

    this.ucMessages[uc.id] = { ok: true, text: `✅ Daten für ${uc.id} vorgeladen — navigiere zu: ${uc.targetRoute}` };

    // Nach kurzem Delay navigieren
    setTimeout(() => {
      this.router.navigate([uc.targetRoute]).catch(() =>
        this.ucMessages[uc.id] = { ok: false, text: `Route ${uc.targetRoute} nicht gefunden — manuell navigieren` }
      );
    }, 600);
  }

  // ── Admin Actions ─────────────────────────────────────────────────────────────

  runSanctionsScan() {
    this.txService.runSanctionsScan().subscribe({
      next: () => this.ucMessages['UC-22'] = { ok: true, text: '✅ Sanctions-Scan gestartet (läuft asynchron)' },
      error: err => this.ucMessages['UC-22'] = { ok: false, text: `❌ ${err?.error?.message ?? 'Fehler beim Scan'}` }
    });
  }

  downloadExport(type: 'camt053' | 'camt054' | 'camt029' | 'datev') {
    const obs$ = type === 'camt053' ? this.txService.downloadCamt053()
               : type === 'camt054' ? this.txService.downloadCamt054()
               : type === 'camt029' ? this.txService.downloadCamt029()
               : this.txService.downloadDatev();

    const ucId = type === 'camt053' ? 'UC-07' : type === 'datev' ? 'UC-08' : type === 'camt054' ? 'UC-29a' : 'UC-29b';
    const ext  = type.startsWith('camt') ? 'xml' : 'csv';
    const mime = type.startsWith('camt') ? 'application/xml' : 'text/csv';

    obs$.subscribe({
      next: blob => {
        const url = URL.createObjectURL(new Blob([blob], { type: mime }));
        const a = document.createElement('a'); a.href = url; a.download = `${type}-export.${ext}`; a.click();
        URL.revokeObjectURL(url);
        this.ucMessages[ucId] = { ok: true, text: `✅ ${type.toUpperCase()} heruntergeladen` };
      },
      error: err => this.ucMessages[ucId] = { ok: false, text: `❌ Download fehlgeschlagen: ${err?.error?.message ?? 'Server-Fehler'}` }
    });
  }

  // ── Webhook Simulator ──────────────────────────────────────────────────────────

  openWebhookModal() {
    this.webhookResult = null;
    this.generateHash();
    this.showWebhookModal = true;
  }

  generateHash() {
    this.webhook.blockchainHash = '0x' + Array.from({ length: 64 }, () =>
      Math.floor(Math.random() * 16).toString(16)).join('');
  }

  triggerWebhook() {
    this.webhookLoading = true;
    this.webhookResult = null;

    this.txService.triggerInboundWebhook({
      walletId: this.webhook.walletId,
      amount: this.webhook.amount,
      currency: this.webhook.currency,
      blockchainHash: this.webhook.blockchainHash,
      senderWallet: this.webhook.senderWallet
    }).subscribe({
      next: res => {
        this.webhookLoading = false;
        this.webhookResult = {
          ok: true,
          text: `TX ${res.transactionId}\nStatus: ${res.status}\nTyp: ${res.type}`
        };
        this.generateHash(); // frischen Hash für nächsten Test
      },
      error: err => {
        this.webhookLoading = false;
        this.webhookResult = {
          ok: false,
          text: JSON.stringify(err.error ?? err, null, 2)
        };
      }
    });
  }
}
