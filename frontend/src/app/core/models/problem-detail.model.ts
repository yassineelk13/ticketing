export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>; // validation errors (400)
}