import { Injectable } from '@angular/core';

const IBAN_MAP: Record<string, string> = {
  'cust-b2b-001': 'DE89370400440532013000',
  'cust-b2c-001': 'DE27200400600532013001',
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

  getIban(): string {
    const id = this.getCustomerId();
    return id ? (IBAN_MAP[id] ?? '') : '';
  }
}
