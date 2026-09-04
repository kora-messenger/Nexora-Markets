import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * loginUser — sign in with email + password.
 * {token, email, password} -> {status, session:{token, expiresAt}, user}
 * Same wrong-password error as unknown email — no account enumeration.
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
  console.error('loginUser error:', err);
  return Response.json({ status: 'error', message: 'Something went wrong. Please try again.' }, { status: 500 });
}

Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    if (req.method !== 'POST') throw new AuthError('POST only', 405);
    const body = await req.json().catch(() => null);
    await requireAppToken(base44, body?.token);

    if (typeof body?.email !== 'string' || !body.email.trim()) throw new AuthError('email is required', 400);
    const email = body.email.trim().toLowerCase();
    const password = body?.password;
    if (typeof password !== 'string' || !password) throw new AuthError('password is required', 400);

    const users = await base44.asServiceRole.entities.AppUser.filter({ email }, undefined, 1);
    const user = (users ?? [])[0];
    if (!user) throw new AuthError('Email or password is incorrect', 401);

    const candidateHash = await hashPassword(password, user.salt);
    if (candidateHash !== user.password_hash) {
      throw new AuthError('Email or password is incorrect', 401);
    }

    const sessionToken = randomHex(32);
    const tokenHash = await sha256Hex(sessionToken);
    const expiresAt = new Date(Date.now() + SESSION_TTL_DAYS * 86400000).toISOString();
    await base44.asServiceRole.entities.UserSession.create({
      user_id: user.id, token_hash: tokenHash, expires_at: expiresAt
    });

    return Response.json({
      status: 'ok',
      session: { token: sessionToken, expiresAt },
      user: { email: user.email, displayName: user.display_name ?? user.email.split('@')[0] },
    });
  } catch (err) {
    return errorResponse(err);
  }
});
