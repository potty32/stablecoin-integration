import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: '/b2b/transfers', pathMatch: 'full' },
  {
    path: 'b2b',
    loadChildren: () => import('./features/b2b/b2b.routes').then(m => m.B2B_ROUTES)
  },
  {
    path: 'b2c',
    loadChildren: () => import('./features/b2c/b2c.routes').then(m => m.B2C_ROUTES)
  },
  { path: '**', redirectTo: '/b2b/transfers' }
];
