import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * grantSubscription — operator-only endpoint that activates Pro on an
 * account (manual activation until automated checkout goes live).
 * {token, email, days?} -> {status:'ok', access:{...}}
 * token must match the ApiCredential record with service='nexora-admin'.
 */
class AuthError extends Error {
  constructor(message: string, public status: number) { super(message); }
}
function errorResponse(err: unknown) {
  if (err instanceof AuthError) {
    return Response.json({ status: 'error', message: err.message }, { status: err.status });
  }
  console.error('grantSubscription error:', err);
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
      (c: any) => c.service === 'nexora-admin' && typeof c.token === 'string' && c.token === body.token
    );
    if (!valid) throw new AuthError('invalid token', 401);

    const email = typeof body?.email === 'string' ? body.email.trim().toLowerCase() : '';
    if (!email) throw new AuthError('email is required', 400);
    const days = body?.days === undefined || body?.days === null || body?.days === '' ? 30 : Number(body.days);
    if (!Number.isInteger(days) || days <= 0 || days > 3650) {
      throw new AuthError('days must be a whole number between 1 and 3650', 400);
    }

    const users = await base44.asServiceRole.entities.AppUser.filter({ email }, undefined, 1);
    const user = (users ?? [])[0];
    if (!user) throw new AuthError('no account found for this email', 404);

    const expiresAt = new Date(Date.now() + days * 86400000).toISOString();
    await base44.asServiceRole.entities.AppUser.update(user.id, {
      subscription_active: true,
      subscription_expires_at: expiresAt,
    });

    // Close out any pending request from this user.
    const pending = await base44.asServiceRole.entities.SubscriptionRequest.filter(
      { user_id: user.id, status: 'pending' }
    );
    for (const r of pending ?? []) {
      await base44.asServiceRole.entities.SubscriptionRequest.update(r.id, { status: 'granted' });
    }

    return Response.json({
      status: 'ok',
      access: { mode: 'active', daysLeft: days, endsAt: expiresAt, email: user.email },
    });
  } catch (err) {
    return errorResponse(err);
  }
});
