import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * validateSession — the app calls this at launch to confirm a stored
 * session is still alive. Expired sessions are deleted server-side.
 * {token, sessionToken} -> {status:'ok', user:{email, displayName}}
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
async function requireAppToken(base44: any, token: unknown) {
  if (typeof token !== 'string' || !token) throw new AuthError('token is required', 400);
  const creds = await base44.asServiceRole.entities.ApiCredential.list();
  const valid = (creds ?? []).some(
    (c: any) => c.service === 'nexora' && typeof c.token === 'string' && c.token === token
  );
  if (!valid) throw new AuthError('invalid token', 401);
}
function errorResponse(err: unknown) {
  if (err instanceof AuthError) {
    return Response.json({ status: 'error', message: err.message }, { status: err.status });
  }
  console.error('validateSession error:', err);
  return Response.json({ status: 'error', message: 'Something went wrong. Please try again.' }, { status: 500 });
}


// ---- Access state (7-day trial -> subscription) ----
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

Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    if (req.method !== 'POST') throw new AuthError('POST only', 405);
    const body = await req.json().catch(() => null);
    await requireAppToken(base44, body?.token);

    const sessionToken = body?.sessionToken;
    if (typeof sessionToken !== 'string' || !sessionToken) {
      throw new AuthError('sessionToken is required', 400);
    }
    const tokenHash = await sha256Hex(sessionToken);
    const sessions = await base44.asServiceRole.entities.UserSession.filter({ token_hash: tokenHash }, undefined, 1);
    const session = (sessions ?? [])[0];
    if (!session) throw new AuthError('session expired', 401);
    if (new Date(session.expires_at).getTime() < Date.now()) {
      await base44.asServiceRole.entities.UserSession.delete(session.id);
      throw new AuthError('session expired', 401);
    }

    const users = await base44.asServiceRole.entities.AppUser.filter({ id: session.user_id }, undefined, 1);
    const user = (users ?? [])[0];
    if (!user) throw new AuthError('account not found', 401);

    return Response.json({
      status: 'ok',
      user: { email: user.email, displayName: user.display_name ?? user.email.split('@')[0] },
      access: computeAccess(user),
    });
  } catch (err) {
    return errorResponse(err);
  }
});
