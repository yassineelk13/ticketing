import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'events' },
  {
    path: 'events',
    title: 'Événements',
    loadComponent: () =>
      import('./features/events/events-list/events-list').then((m) => m.EventsList),
  },
  {
    path: 'events/:id',
    title: 'Détail de l’événement',
    loadComponent: () =>
      import('./features/events/event-detail/event-detail').then((m) => m.EventDetail),
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
    path: 'tickets',
    title: 'Mes billets',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/tickets/my-tickets/my-tickets').then((m) => m.MyTickets),
  },
  {
    path: 'profile',
    title: 'Mon profil',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile/profile').then((m) => m.Profile),
  },
  {
    path: '**',
    title: 'Page introuvable',
    loadComponent: () => import('./shared/not-found/not-found').then((m) => m.NotFound),
  },
];
