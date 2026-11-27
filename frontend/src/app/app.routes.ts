import { Routes } from '@angular/router';
import { guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'events' },
  {
    path: 'events',
    title: 'Événements',
    loadComponent: () =>
      import('./features/events/events-list/events-list').then((m) => m.EventsList),
  },
  {
    path: 'login',
    title: 'Connexion',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Inscription',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: '**',
    title: 'Page introuvable',
    loadComponent: () => import('./shared/not-found/not-found').then((m) => m.NotFound),
  },
];
