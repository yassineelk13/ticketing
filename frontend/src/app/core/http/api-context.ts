import { HttpContextToken } from '@angular/common/http';

/** Set to true on a request to skip the global error snackbar (the caller handles the error). */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);
