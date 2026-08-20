import { Injectable } from '@angular/core';

// Mandanten-spezifische IBANs (V24-Seed + V25-Fixes)
// Schlüssel: `${customerId}@${tenantId}`
const IBAN_MAP: Record<string, string> = {
  // tenant-kleine-vb (V26: 090xxx — kollisionsfrei mit E2E-Tests)
  'cust-b2b-001@tenant-kleine-vb':      'DE89370400440532090001',
  'cust-b2b-approver@tenant-kleine-vb': 'DE89370400440532090002',
  'cust-b2c-001@tenant-kleine-vb':      'DE27200400600532090001',
  // tenant-grosse-vb
  'cust-b2b-001@tenant-grosse-vb':      'DE89370400440532090003',
  'cust-b2c-001@tenant-grosse-vb':      'DE27200400600532090003',
  // tenant-default (Fallback)
  'cust-b2b-001@tenant-default':        'DE89370400440532013000',
  'cust-b2c-001@tenant-default':        'DE27200400600532013001',
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  getCustomerId(): string | null {
    const token = localStorage.getItem('access_token');
    if (!token) return null;
    try {
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
      return decoded.sub ?? null;
    } catch {
      return null;
    }
  }

  getTenantId(): string {
    return localStorage.getItem('tenant_id') ?? 'tenant-default';
  }

  getIban(): string {
    const customerId = this.getCustomerId();
    const tenantId   = this.getTenantId();
    if (!customerId) return '';
    const key = `${customerId}@${tenantId}`;
    return IBAN_MAP[key] ?? IBAN_MAP[`${customerId}@tenant-default`] ?? '';
  }
}
