import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * registerUser — create a Nexora account.
 * {token, email, password, displayName?} -> {status, session:{token, expiresAt}, user}
 * Security: PBKDF2-SHA256 (100k iterations — platform max) password hashing with per-user
 * salt; sessions are random 32-byte tokens, only their SHA-256 stored;
 * 30-day expiry. Requires the Nexora app credential (same gate as analyzeChart).
 */
const SESSION_TTL_DAYS = 30;

class AuthError extends Error {
  constructor(message: string, public status: number) { super(message); }
}

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}
function hexToBytes(hex: string): Uint8Array {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  return out;
}
function randomHex(byteLength: number): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return bytesToHex(bytes);
}
async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
  return bytesToHex(new Uint8Array(digest));
}
async function hashPassword(password: string, saltHex: string): Promise<string> {
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: hexToBytes(saltHex), iterations: 100000 }, key, 256);
  return bytesToHex(new Uint8Array(bits));
}
function normalizeEmail(email: unknown): string {
  if (typeof email !== 'string') throw new AuthError('email is required', 400);
  const e = email.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e)) throw new AuthError('enter a valid email address', 400);
  return e;
}
function requirePassword(password: unknown): string {
  if (typeof password !== 'string' || password.length < 8) {
    throw new AuthError('password must be at least 8 characters', 400);
  }
  if (!/[a-zA-Z]/.test(password) || !/[0-9]/.test(password)) {
    throw new AuthError('password needs at least one letter and one number', 400);
  }
  return password;
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
  console.error('registerUser error:', err);
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

    const email = normalizeEmail(body?.email);
    const password = requirePassword(body?.password);
    const displayName = typeof body?.displayName === 'string' && body.displayName.trim()
      ? body.displayName.trim().slice(0, 40)
      : email.split('@')[0];

    const existing = await base44.asServiceRole.entities.AppUser.filter({ email }, undefined, 1);
    if ((existing ?? []).length > 0) {
      throw new AuthError('An account with this email already exists. Try signing in instead.', 409);
    }

    const salt = randomHex(16);
    const passwordHash = await hashPassword(password, salt);
    const created = await base44.asServiceRole.entities.AppUser.create({
      email, password_hash: passwordHash, salt, display_name: displayName
    });

    const sessionToken = randomHex(32);
    const tokenHash = await sha256Hex(sessionToken);
    const expiresAt = new Date(Date.now() + SESSION_TTL_DAYS * 86400000).toISOString();
    await base44.asServiceRole.entities.UserSession.create({
      user_id: created.id, token_hash: tokenHash, expires_at: expiresAt
    });

    const access = { mode: 'trial', daysLeft: TRIAL_DAYS, endsAt: new Date(Date.now() + TRIAL_DAYS * 86400000).toISOString() };
    return Response.json({ status: 'ok', session: { token: sessionToken, expiresAt }, user: { email, displayName }, access });
  } catch (err) {
    return errorResponse(err);
  }
});
