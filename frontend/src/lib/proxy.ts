/**
 * Reading a response from the backend in the same-origin proxy routes.
 *
 * `response.json()` throws on an empty body, and the backend sends an empty
 * body with 401 and 403. Every proxy route wrapped that call in a try/catch
 * that reported the failure as a 502 outage -- so being logged out was
 * indistinguishable from the API being down, and the message shown to the user
 * said the feature was unavailable when they only needed to sign in.
 */
/** Parsed backend body, or null when there was none (a 401 sends no body). */
export type BackendPayload = Record<string, any> | null;

export async function readBackendJson(response: Response): Promise<BackendPayload> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    // A proxy or gateway returning HTML, most likely. Surface the status
    // rather than the body, which is not ours to forward.
    return { error: `Backend returned ${response.status}` };
  }
}
