import { Routes } from '@angular/router';

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
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: '**',
    title: 'Page introuvable',
    loadComponent: () => import('./shared/not-found/not-found').then((m) => m.NotFound),
  },
];
