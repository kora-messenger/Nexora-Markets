import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * getTradingProfile — fetch the caller's personalization profile.
 * {token, sessionToken} -> {status:'ok', profile: {...} | null}
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
async function requireUser(base44: any, sessionToken: unknown): Promise<any> {
  if (typeof sessionToken !== 'string' || !sessionToken) throw new AuthError('sessionToken is required', 400);
  const tokenHash = await sha256Hex(sessionToken);
  const sessions = await base44.asServiceRole.entities.UserSession.filter({ token_hash: tokenHash }, undefined, 1);
  const session = (sessions ?? [])[0];
  if (!session) throw new AuthError('session expired', 401);
  if (new Date(session.expires_at).getTime() < Date.now()) throw new AuthError('session expired', 401);
  return session;
}
function errorResponse(err: unknown) {
  if (err instanceof AuthError) {
    return Response.json({ status: 'error', message: err.message }, { status: err.status });
  }
  console.error('getTradingProfile error:', err);
  return Response.json({ status: 'error', message: 'Something went wrong. Please try again.' }, { status: 500 });
}

Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    if (req.method !== 'POST') throw new AuthError('POST only', 405);
    const body = await req.json().catch(() => null);
    await requireAppToken(base44, body?.token);
    const session = await requireUser(base44, body?.sessionToken);

    const existing = await base44.asServiceRole.entities.TradingProfile.filter({ user_id: session.user_id }, undefined, 1);
    const record = (existing ?? [])[0];
    return Response.json({
      status: 'ok',
      profile: record
        ? {
            experienceLevel: record.experience_level ?? null,
            primaryGoal: record.primary_goal ?? null,
            capitalUsd: record.capital_usd ?? null,
            instruments: record.instruments ?? null,
            tradingStyle: record.trading_style ?? null,
            riskTolerance: record.risk_tolerance ?? null,
          }
        : null,
    });
  } catch (err) {
    return errorResponse(err);
  }
});
