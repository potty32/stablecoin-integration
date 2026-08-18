import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { Router } from '@angular/router';

const authGuard = () => {
  if (localStorage.getItem('access_token')) return true;
  return inject(Router).createUrlTree(['/dev-portal']);
};

export const routes: Routes = [
  {
    path: '',
    redirectTo: '/dev-portal',
    pathMatch: 'full'
  },
  {
    path: 'dev-portal',
    loadComponent: () => import('./features/dev-portal/dev-portal.component').then(m => m.DevPortalComponent)
  },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'b2b',
    canActivate: [authGuard],
    loadChildren: () => import('./features/b2b/b2b.routes').then(m => m.B2B_ROUTES)
  },
  {
    path: 'b2c',
    canActivate: [authGuard],
    loadChildren: () => import('./features/b2c/b2c.routes').then(m => m.B2C_ROUTES)
  },
  { path: '**', redirectTo: '/dev-portal' }
];
