import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE } from '../../core/api.config';
import { SILENT_ERRORS } from '../../core/http/api-context';
import { UpdateProfileRequest, UserResponse } from '../../core/models/user.model';

@Service()
export class UserService {
  private readonly http = inject(HttpClient);
  // The profile page shows its own error states and field errors.
  private readonly silentErrors = new HttpContext().set(SILENT_ERRORS, true);

  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${API_BASE}/users/me`, { context: this.silentErrors });
  }

  updateMe(request: UpdateProfileRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${API_BASE}/users/me`, request, {
      context: this.silentErrors,
    });
  }
}
