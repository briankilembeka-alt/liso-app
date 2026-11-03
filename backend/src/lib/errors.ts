/** An error the API returns to the client as { error: { code, message } }. */
export class AppError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export const notFound = (what: string) => new AppError(404, "NOT_FOUND", `${what} not found`);
export const forbidden = (message = "not allowed") => new AppError(403, "FORBIDDEN", message);
export const conflict = (code: string, message: string) => new AppError(409, code, message);
