import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * analyzeChart — Nexora Markets cloud AI endpoint (Base44 backend).
 * The Android app sends chart screenshot(s) + timeframe/style, and this
 * function runs the vision analysis server-side with the AI provider key
 * held securely in Base44 secrets (never shipped inside the APK).
 * The prompt and response contract mirror the app's on-device BYO-key mode
 * so both paths produce the identical structured trade plan.
 */

const TRIAL_DAYS = 7;
function computeAccess(user: any) {
  const now = Date.now();
  const subActive = user.subscription_active === true &&
    (!user.subscription_expires_at || new Date(user.subscription_expires_at).getTime() > now);
  if (subActive) return { mode: 'active' };
  const trialEnd = new Date(user.created_date).getTime() + TRIAL_DAYS * 86400000;
  return now < trialEnd ? { mode: 'trial' } : { mode: 'expired' };
}
function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes).map((b) => b.toString(16).padStart(2, '0')).join('');
}
async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
  return bytesToHex(new Uint8Array(digest));
}

Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    if (req.method !== 'POST') {
      return Response.json({ status: 'error', message: 'POST only' }, { status: 405 });
    }
    const body = await req.json();
    const { token, images, timeframe, trading_style: tradingStyle, instrument, sessionToken } = body ?? {};

    if (!token) {
      return Response.json({ status: 'error', message: 'token is required' }, { status: 400 });
    }
    const creds = await base44.asServiceRole.entities.ApiCredential.list();
    const kind = (creds ?? []).find(
      (c) => typeof c.token === 'string' && c.token === token
    )?.service ?? null;
    if (kind !== 'nexora' && kind !== 'nexora-pipeline') {
      return Response.json({ status: 'error', message: 'invalid token' }, { status: 401 });
    }

    // App calls must ride a live session with active access (7-day trial, then subscription).
    // The publishing pipeline uses its own service credential and is not user-gated.
    if (kind === 'nexora') {
      if (typeof sessionToken !== 'string' || !sessionToken) {
        return Response.json({ status: 'error', message: 'sessionToken is required' }, { status: 400 });
      }
      const tokenHash = await sha256Hex(sessionToken);
      const sessions = await base44.asServiceRole.entities.UserSession.filter({ token_hash: tokenHash }, undefined, 1);
      const session = (sessions ?? [])[0];
      if (!session || new Date(session.expires_at).getTime() < Date.now()) {
        return Response.json({ status: 'error', message: 'session expired' }, { status: 401 });
      }
      const users = await base44.asServiceRole.entities.AppUser.filter({ id: session.user_id }, undefined, 1);
      const user = (users ?? [])[0];
      if (!user) {
        return Response.json({ status: 'error', message: 'account not found' }, { status: 401 });
      }
      const access = computeAccess(user);
      if (access.mode === 'expired') {
        return Response.json(
          {
            status: 'error',
            needsSubscription: true,
            message: 'Your 7-day Pro trial has ended. Subscribe to keep using Nexora AI analysis.',
          },
          { status: 402 }
        );
      }
    }

    const imageList = Array.isArray(images) ? images.filter((i) => typeof i === 'string' && i.length > 0) : [];
    if (imageList.length === 0) {
      return Response.json({ status: 'error', message: 'at least one chart image is required' }, { status: 400 });
    }
    if (imageList.length > 2) {
      return Response.json({ status: 'error', message: 'at most two chart images are supported' }, { status: 400 });
    }

    const apiKey = Deno.env.get('AI_API_KEY') ?? Deno.env.get('OPENAI_API_KEY') ?? '';
    const baseUrl = (Deno.env.get('AI_BASE_URL') ?? 'https://api.openai.com/v1').replace(/\/+$/, '');
    const model = Deno.env.get('AI_MODEL') ?? 'gpt-4o-mini';
    if (!apiKey) {
      return Response.json(
        {
          status: 'error',
          message: 'AI provider is not configured on the server yet. Add an AI_API_KEY secret (OpenAI, Groq, OpenRouter or any OpenAI-compatible provider), or use your own key in the app settings.',
        },
        { status: 503 }
      );
    }

    const tf = typeof timeframe === 'string' && timeframe ? timeframe : '4H';
    const style = typeof tradingStyle === 'string' && tradingStyle ? tradingStyle : 'Scalp';
    const styleText = style.toLowerCase().startsWith('swing')
      ? 'a swing trader (positions held days to weeks)'
      : 'a scalper (positions held minutes to hours)';
    const hint = typeof instrument === 'string' && instrument ? `The trader says the instrument is: ${instrument}. ` : '';
    const multi = imageList.length > 1
      ? 'Two screenshots are attached: use the higher timeframe for trend bias and the lower timeframe for entry timing (multi-timeframe confluence). '
      : '';

    const prompt =
      `You are a senior technical analyst briefing ${styleText} on a chart.` +
      hint +
      `Stated timeframe(s): ${tf}. ` +
      multi +
      'Read the chart like a professional: instrument, market structure (trend, support/resistance zones), notable candlestick patterns, and any visible indicators (RSI, MACD, moving averages). ' +
      'Reply with ONLY a JSON object, no markdown fences, with exactly these keys: ' +
      '{"signal": "BUY" | "SELL" | "NEUTRAL", "confidence": "Low" | "Medium" | "High", ' +
      '"entry": "entry zone as a price range string", "stop_loss": "stop loss price string", ' +
      '"take_profits": ["TP1 price", "TP2 price"], "key_levels": ["level 1 description", "level 2 description"], ' +
      '"reasoning": "2-4 sentences of concrete, chart-grounded reasoning"} ' +
      'Use real price levels visible on the chart. Be decisive but honest about weak setups (use NEUTRAL).';

    const content = [{ type: 'text', text: prompt }];
    for (const img of imageList) {
      const url = img.startsWith('data:') ? img : `data:image/jpeg;base64,${img}`;
      content.push({ type: 'image_url', image_url: { url } });
    }

    const aiBody = {
      model,
      temperature: 0.3,
      messages: [{ role: 'user', content }],
    };

    const aiRes = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(aiBody),
    });

    if (!aiRes.ok) {
      const errText = (await aiRes.text()).slice(0, 300);
      return Response.json(
        { status: 'error', message: `AI provider error (HTTP ${aiRes.status}): ${errText}` },
        { status: 502 }
      );
    }

    const aiJson = await aiRes.json();
    const text = aiJson?.choices?.[0]?.message?.content ?? '';
    if (!text) {
      return Response.json({ status: 'error', message: 'AI provider returned an empty response' }, { status: 502 });
    }

    const cleaned = text.trim().replace(/^```json\s*/i, '').replace(/^```\s*/, '').replace(/```\s*$/, '').trim();
    let analysis;
    try {
      const parsed = JSON.parse(cleaned);
      analysis = {
        signal: typeof parsed.signal === 'string' ? parsed.signal.toUpperCase() : 'NEUTRAL',
        confidence: typeof parsed.confidence === 'string' ? parsed.confidence : null,
        entry: typeof parsed.entry === 'string' ? parsed.entry : null,
        stop_loss: typeof parsed.stop_loss === 'string' ? parsed.stop_loss : null,
        take_profits: Array.isArray(parsed.take_profits) ? parsed.take_profits.filter((t) => typeof t === 'string') : [],
        key_levels: Array.isArray(parsed.key_levels) ? parsed.key_levels.filter((k) => typeof k === 'string') : [],
        reasoning: typeof parsed.reasoning === 'string' ? parsed.reasoning : cleaned,
      };
    } catch (_) {
      analysis = {
        signal: 'NEUTRAL',
        confidence: null,
        entry: null,
        stop_loss: null,
        take_profits: [],
        key_levels: [],
        reasoning: cleaned,
      };
    }

    return Response.json({ status: 'ok', analysis });
  } catch (err) {
    return Response.json({ status: 'error', message: err?.message ?? 'unexpected error' }, { status: 500 });
  }
});
