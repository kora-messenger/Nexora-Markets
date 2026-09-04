import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * getAccessState — the caller's trial/subscription status.
 * {token, sessionToken} -> {status:'ok', access:{mode:'trial'|'active'|'expired', daysLeft, endsAt}}
 *
 * Trial: 7 days from account creation. After that a subscription is required.
 */
class AuthError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

const TRIAL_DAYS = 7;
function computeAccess(user: any) {
  const now = Date.now();
  const subActive = user.subscription_active === true &&
    (!user.subscription_expires_at || new Date(user.subscription_expires_at).getTime() > now);
  if (subActive) {
    return { mode: 'active', daysLeft: null, endsAt: user.subscription_expires_at ?? null };
  }
  const trialEnd = new Date(user.created_date).getTime() + TRIAL_DAYS * 86400000;
  if (now < trialEnd) {
    return {
      mode: 'trial',
      daysLeft: Math.max(1, Math.ceil((trialEnd - now) / 86400000)),
      endsAt: new Date(trialEnd).toISOString(),
    };
  }
  return { mode: 'expired', daysLeft: 0, endsAt: null };
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
  console.error('getAccessState error:', err);
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

    const users = await base44.asServiceRole.entities.AppUser.filter({ id: session.user_id }, undefined, 1);
    const user = (users ?? [])[0];
    if (!user) throw new AuthError('account not found', 401);

    return Response.json({ status: 'ok', access: computeAccess(user) });
  } catch (err) {
    return errorResponse(err);
  }
});
