import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/notification/notification.service';
import { reservationErrorMessage } from '../reservation-errors';
import { TicketService } from '../ticket.service';

@Component({
  selector: 'app-reserve-button',
  imports: [RouterLink, MatButtonModule],
  templateUrl: './reserve-button.html',
  styleUrl: './reserve-button.scss',
})
export class ReserveButton {
  readonly eventId = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly tickets = inject(TicketService);
  private readonly notifications = inject(NotificationService);

  protected readonly reserving = signal(false);
  protected readonly confirmed = signal(false);
  protected readonly loginReturnUrl = computed(() => `/events/${this.eventId()}`);

  protected reserve(): void {
    if (this.reserving()) {
      return;
    }
    this.reserving.set(true);
    this.confirmed.set(false);
    this.tickets.reserve(this.eventId()).subscribe({
      next: () => {
        this.reserving.set(false);
        this.confirmed.set(true);
      },
      error: (error: HttpErrorResponse) => {
        this.reserving.set(false);
        this.notifications.error(reservationErrorMessage(error.status));
      },
    });
  }
}
