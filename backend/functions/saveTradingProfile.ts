import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * saveTradingProfile — upsert the caller's personalization profile.
 * {token, sessionToken, experienceLevel?, tradingSessions?, tradeFrequency?,
 *  holdDuration?, riskPercent?, instruments?, capitalUsd?, confluenceBiasTf?,
 *  confluenceTriggerTf?, entryNotes?} -> {status:'ok', profile}
 * Identity comes from sessionToken — never trust a client-supplied user id.
 */
class AuthError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

const EXPERIENCE_LEVELS = ['Beginner', 'Intermediate', 'Advanced'];
const SESSIONS = ['Asia', 'London', 'New York', 'All sessions'];
const FREQUENCY = ['1-3 a week', '4-10 a week', '10+ a week'];
const DURATIONS = ['Minutes', 'Hours', 'Days', 'Weeks'];
const INSTRUMENTS = ['Forex', 'Crypto', 'Both'];
const RISK_PERCENTS = [0.5, 1, 2, 3];
const BIAS_TIMEFRAMES = ['1D', '4H', '1H'];
const TRIGGER_TIMEFRAMES = ['1H', '15M', '5M', '1M'];
const ENTRY_NOTES_MAX = 500;

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
    const experienceLevel = pickEnum(body?.experienceLevel, EXPERIENCE_LEVELS, 'experienceLevel');
    const tradingSessions = pickEnum(body?.tradingSessions, SESSIONS, 'tradingSessions');
    const tradeFrequency = pickEnum(body?.tradeFrequency, FREQUENCY, 'tradeFrequency');
    const holdDuration = pickEnum(body?.holdDuration, DURATIONS, 'holdDuration');
    const instruments = pickEnum(body?.instruments, INSTRUMENTS, 'instruments');
    const confluenceBiasTf = pickEnum(body?.confluenceBiasTf, BIAS_TIMEFRAMES, 'confluenceBiasTf');
    const confluenceTriggerTf = pickEnum(body?.confluenceTriggerTf, TRIGGER_TIMEFRAMES, 'confluenceTriggerTf');
    if (experienceLevel) patch.experience_level = experienceLevel;
    if (tradingSessions) patch.trading_sessions = tradingSessions;
    if (tradeFrequency) patch.trade_frequency = tradeFrequency;
    if (holdDuration) patch.hold_duration = holdDuration;
    if (instruments) patch.instruments = instruments;
    if (confluenceBiasTf) patch.confluence_bias_tf = confluenceBiasTf;
    if (confluenceTriggerTf) patch.confluence_trigger_tf = confluenceTriggerTf;
    if (body?.riskPercent !== undefined && body?.riskPercent !== null && body?.riskPercent !== '') {
      const risk = Number(body.riskPercent);
      if (!RISK_PERCENTS.includes(risk)) throw new AuthError('riskPercent must be one of: 0.5, 1, 2, 3', 400);
      patch.risk_percent = risk;
    }
    if (body?.capitalUsd !== undefined && body?.capitalUsd !== null && body?.capitalUsd !== '') {
      const capital = Number(body.capitalUsd);
      if (!Number.isFinite(capital) || capital <= 0) throw new AuthError('capitalUsd must be a positive number', 400);
      patch.capital_usd = capital;
    }
    if (body?.entryNotes !== undefined && body?.entryNotes !== null) {
      const notes = String(body.entryNotes).trim();
      if (notes.length > ENTRY_NOTES_MAX) {
        throw new AuthError(`entryNotes must be ${ENTRY_NOTES_MAX} characters or fewer`, 400);
      }
      patch.entry_notes = notes;
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
    tradingSessions: record.trading_sessions ?? null,
    tradeFrequency: record.trade_frequency ?? null,
    holdDuration: record.hold_duration ?? null,
    riskPercent: record.risk_percent ?? null,
    instruments: record.instruments ?? null,
    capitalUsd: record.capital_usd ?? null,
    confluenceBiasTf: record.confluence_bias_tf ?? null,
    confluenceTriggerTf: record.confluence_trigger_tf ?? null,
    entryNotes: record.entry_notes ?? null,
  };
}
