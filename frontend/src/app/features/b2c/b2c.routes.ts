import { Routes } from '@angular/router';

export const B2C_ROUTES: Routes = [
  {
    path: 'remittances',
    loadComponent: () =>
      import('./remittance/remittance-form.component').then(m => m.RemittanceFormComponent)
  },
  {
    path: 'p2p',
    loadComponent: () =>
      import('./p2p/p2p-phone.component').then(m => m.P2pPhoneComponent)
  },
  {
    path: 'yield',
    loadComponent: () =>
      import('./yield/yield-position.component').then(m => m.YieldPositionComponent)
  },
  { path: '', redirectTo: 'remittances', pathMatch: 'full' }
];
