import { Routes } from '@angular/router';

export const B2B_ROUTES: Routes = [
  {
    path: 'transfers',
    loadComponent: () =>
      import('./transfer/transfer-list.component').then(m => m.TransferListComponent)
  },
  {
    path: 'transfers/new',
    loadComponent: () =>
      import('./transfer/transfer-form.component').then(m => m.TransferFormComponent)
  },
  {
    path: 'approvals',
    loadComponent: () =>
      import('./approval/approval-dashboard.component').then(m => m.ApprovalDashboardComponent)
  },
  {
    path: 'address-book',
    loadComponent: () =>
      import('./address-book/address-book.component').then(m => m.AddressBookComponent)
  }
];
