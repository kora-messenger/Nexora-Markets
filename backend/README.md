# Nexora Cloud backend

Standard Deno `Deno.serve` functions — currently hosted on Base44 (zero-setup
HTTPS endpoint, no domain required).

| Endpoint | Used by | Purpose |
|---|---|---|
| `analyzeChart` | Android app | Vision AI chart analysis (provider key held server-side) |
| `saveMarketReport` | Analysis pipeline | Publish the daily signals report |
| `latestMarketReport` | Android app | Read the latest daily report |

**Migrating off Base44:** host these on any Deno runtime behind your domain.
The `@base44/sdk` entity calls (report storage + credential check) are the only
Base44-specific parts — replace them with your database of choice and point the
app/pipeline URLs at the new host. The request/response contracts stay identical.
