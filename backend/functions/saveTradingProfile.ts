import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * saveTradingProfile — upsert the caller's personalization profile.
 * {token, sessionToken, experienceLevel?, primaryGoal?, capitalUsd?,
 *  instruments?, tradingStyle?, riskTolerance?} -> {status:'ok', profile}
 * Identity comes from sessionToken (same session vault as the rest of
 * auth) — never trust a client-supplied user id.
 */
class AuthError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

const GOALS = ['Consistent monthly income', 'Account growth', 'Funded trader status', 'Retirement savings', 'Quit the 9-to-5'];
const LEVELS = ['Beginner', 'Intermediate', 'Advanced'];
const INSTRUMENTS = ['Forex', 'Crypto', 'Both'];
const STYLES = ['Scalping', 'Day trading', 'Swing trading', 'Position trading'];
const RISK = ['Conservative', 'Moderate', 'Aggressive'];

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
function pickEnum(value: unknown, allowed: string[], field: string): string | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  if (typeof value !== 'string' || !allowed.includes(value)) {
    throw new AuthError(`${field} must be one of: ${allowed.join(', ')}`, 400);
  }
  return value;
}
function errorResponse(err: unknown) {
  if (err instanceof AuthError) {
    return Response.json({ status: 'error', message: err.message }, { status: err.status });
  }
  console.error('saveTradingProfile error:', err);
  return Response.json({ status: 'error', message: 'Something went wrong. Please try again.' }, { status: 500 });
}

Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    if (req.method !== 'POST') throw new AuthError('POST only', 405);
    const body = await req.json().catch(() => null);
    await requireAppToken(base44, body?.token);
    const session = await requireUser(base44, body?.sessionToken);

    const patch: Record<string, unknown> = {};
    const experienceLevel = pickEnum(body?.experienceLevel, LEVELS, 'experienceLevel');
    const primaryGoal = pickEnum(body?.primaryGoal, GOALS, 'primaryGoal');
    const instruments = pickEnum(body?.instruments, INSTRUMENTS, 'instruments');
    const tradingStyle = pickEnum(body?.tradingStyle, STYLES, 'tradingStyle');
    const riskTolerance = pickEnum(body?.riskTolerance, RISK, 'riskTolerance');
    if (experienceLevel) patch.experience_level = experienceLevel;
    if (primaryGoal) patch.primary_goal = primaryGoal;
    if (instruments) patch.instruments = instruments;
    if (tradingStyle) patch.trading_style = tradingStyle;
    if (riskTolerance) patch.risk_tolerance = riskTolerance;
    if (body?.capitalUsd !== undefined && body?.capitalUsd !== null && body?.capitalUsd !== '') {
      const capital = Number(body.capitalUsd);
      if (!Number.isFinite(capital) || capital < 0) throw new AuthError('capitalUsd must be a positive number', 400);
      patch.capital_usd = capital;
    }

    const existing = await base44.asServiceRole.entities.TradingProfile.filter({ user_id: session.user_id }, undefined, 1);
    const current = (existing ?? [])[0];
    const saved = current
      ? await base44.asServiceRole.entities.TradingProfile.update(current.id, patch)
      : await base44.asServiceRole.entities.TradingProfile.create({ user_id: session.user_id, ...patch });

    return Response.json({ status: 'ok', profile: toProfileDto(saved) });
  } catch (err) {
    return errorResponse(err);
  }
});

function toProfileDto(record: any) {
  return {
    experienceLevel: record.experience_level ?? null,
    primaryGoal: record.primary_goal ?? null,
    capitalUsd: record.capital_usd ?? null,
    instruments: record.instruments ?? null,
    tradingStyle: record.trading_style ?? null,
    riskTolerance: record.risk_tolerance ?? null,
  };
}
