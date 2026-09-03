import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * saveMarketReport — called by the ai-market-analysis GitHub Actions pipeline
 * after each daily run. Validates the shared pipeline token and upserts the
 * day's report into the MarketReport entity (one record per report date).
 */
Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    const body = await req.json();
    const { token, report_date, signals_json, markdown } = body ?? {};

    if (!token || !report_date) {
      return Response.json({ status: 'error', message: 'token and report_date are required' }, { status: 400 });
    }

    const creds = await base44.asServiceRole.entities.ApiCredential.list();
    const valid = (creds ?? []).some(
      (c) => c.service === 'nexora' && typeof c.token === 'string' && c.token === token
    );
    if (!valid) {
      return Response.json({ status: 'error', message: 'invalid token' }, { status: 401 });
    }

    const reports = await base44.asServiceRole.entities.MarketReport.list();
    const existing = (reports ?? []).find((r) => r.report_date === report_date);

    const payload = {
      report_date,
      signals_json: typeof signals_json === 'string' ? signals_json : JSON.stringify(signals_json ?? []),
      markdown: typeof markdown === 'string' ? markdown : '',
    };

    if (existing) {
      await base44.asServiceRole.entities.MarketReport.update(existing.id, payload);
    } else {
      await base44.asServiceRole.entities.MarketReport.create(payload);
    }

    return Response.json({ status: 'ok', report_date, mode: existing ? 'updated' : 'created' });
  } catch (err) {
    return Response.json({ status: 'error', message: err?.message ?? 'unexpected error' }, { status: 500 });
  }
});
