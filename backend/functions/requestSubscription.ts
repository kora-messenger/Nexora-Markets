import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * requestSubscription — a signed-in user asks to be switched to Pro after
 * their trial ends. Idempotent: one pending request per user.
 * {token, sessionToken} -> {status:'ok', request:{state:'pending'|'granted'}}
 */
class AuthError extends Error {
  constructor(message: string, public status: number) { super(message); }
}
function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}
async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
  return bytesToHex(new Uint8Array(digest));
}
function errorResponse(err: unknown) {
  if (err instanceof AuthError) {
    return Response.json({ status: 'error', message: err.message }, { status: err.status });
  }
  console.error('requestSubscription error:', err);
  return Response.json({ status: 'error', message: 'Something went wrong. Please try again.' }, { status: 500 });
}

Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    if (req.method !== 'POST') throw new AuthError('POST only', 405);
    const body = await req.json().catch(() => null);
    if (typeof body?.token !== 'string' || !body.token) throw new AuthError('token is required', 400);
    const creds = await base44.asServiceRole.entities.ApiCredential.list();
    const valid = (creds ?? []).some(
      (c: any) => c.service === 'nexora' && typeof c.token === 'string' && c.token === body.token
    );
    if (!valid) throw new AuthError('invalid token', 401);
    if (typeof body.sessionToken !== 'string' || !body.sessionToken) throw new AuthError('sessionToken is required', 400);

    const tokenHash = await sha256Hex(body.sessionToken);
    const sessions = await base44.asServiceRole.entities.UserSession.filter({ token_hash: tokenHash }, undefined, 1);
    const session = (sessions ?? [])[0];
    if (!session) throw new AuthError('session expired', 401);
    if (new Date(session.expires_at).getTime() < Date.now()) throw new AuthError('session expired', 401);

    const existing = await base44.asServiceRole.entities.SubscriptionRequest.filter(
      { user_id: session.user_id, status: 'pending' }, undefined, 1
    );
    if ((existing ?? []).length > 0) {
      return Response.json({ status: 'ok', request: { state: 'pending' } });
    }
    await base44.asServiceRole.entities.SubscriptionRequest.create({
      user_id: session.user_id, status: 'pending'
    });
    return Response.json({ status: 'ok', request: { state: 'pending' } });
  } catch (err) {
    return errorResponse(err);
  }
});
