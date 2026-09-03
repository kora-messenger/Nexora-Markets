import { createClientFromRequest } from 'npm:@base44/sdk@0.8.31';

/**
 * latestMarketReport — read-only endpoint. Returns the most recent daily
 * market analysis report (signals summary + full markdown) published by
 * the ai-market-analysis pipeline.
 */
Deno.serve(async (req) => {
  const base44 = createClientFromRequest(req);
  try {
    const reports = await base44.asServiceRole.entities.MarketReport.list();
    const sorted = (reports ?? []).slice().sort((a, b) =>
      String(b.report_date ?? '').localeCompare(String(a.report_date ?? ''))
    );
    if (sorted.length === 0) {
      return Response.json({ status: 'ok', report: null, message: 'no reports published yet' });
    }
    const latest = sorted[0];
    let signals = [];
    try {
      signals = latest.signals_json ? JSON.parse(latest.signals_json) : [];
    } catch (_) {
      signals = [];
    }
    return Response.json({
      status: 'ok',
      report: {
        report_date: latest.report_date,
        signals,
        markdown: latest.markdown ?? '',
      },
    });
  } catch (err) {
    return Response.json({ status: 'error', message: err?.message ?? 'unexpected error' }, { status: 500 });
  }
});
