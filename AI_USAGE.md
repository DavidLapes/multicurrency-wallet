# AI usage

## Tools
- **Claude Code** (Claude desktop app): generated most of the code and tests, reviewed the code against best practices, discussed design options with me, and drafted the README section by section.
- **JetBrains AI (Junie)**: cleanup in the IDE: removing comments, making formatting consistent, removing unused and redundant code.

No agent configuration files (such as `CLAUDE.md` or `.junie/guidelines.md`) are committed; all steering happened in the conversation.

## How I steered them
- **Restart in small steps.** The AI's first version was a complete project generated in one go. I deleted it and rebuilt the application piece by piece (Docker, logging, CNB client, wallet, scheduling, observability), reviewing each step before the next.
- **Asked it to push back.** After I renamed `compose.yaml` to a non-standard name without being warned, I told the AI to warn me whenever I ask for something non-standard, and to recommend the standard option first.
- **Questioned complexity.** I regularly asked "why do we need this?": for exception handler overrides, a `Retry-After` header and MapStruct. Each time we kept only what the assignment needs.
- **Checked claims.** When the AI presented the health check, I asked how it actually checks CNB. It doesn't; it reports cached state. We made that explicit (`lastCheckedAt`) and documented it.
- **Consult first for documentation.** Every design decision and README section was proposed to me first and written only after my approval.

## Where the AI was wrong, and how I corrected it
**1. Exchange rate caching: wrong refresh logic.** The AI's first cache asked CNB again every 10 minutes whenever the cached rates were not dated "today". That sounds reasonable, but CNB publishes no rates on weekends or before 14:30, so the app would have called CNB all weekend and every morning for rates that cannot exist yet. I noticed this in review and rejected it ("we shouldn't poll CNB all the time"). I worked out the correct rule with the AI: call CNB only when a *newer fixing should exist*, meaning after 14:30 on a weekday. Later I added a scheduled refresh at 15:00 and moved the CNB-specific schedule (time zone, publication time) out of the service into configuration. The AI implemented each step; I checked the behaviour against tests for weekends, mornings and late publication.

**2. Error handling: over-engineered framework code.** The AI's global exception handler overrode four Spring framework methods, including a workaround for an internal Spring detail (a `null` body that Spring fills in later), only so that errors like 404 or 405 would also carry a custom `code` field. I asked why an assignment needs this. The honest answer was that it doesn't: Spring already returns standard problem responses for those cases. I had three of the four overrides removed, kept only the one that tells clients *which field* failed validation, and later also removed a hard-coded `Retry-After: 60` header that did not match anything the application actually does.

## What I decided or wrote myself
- **Architecture:** the layered package structure; moving the in-memory balances into their own `storage` package and the business exceptions into `domain/exception` (my own changes in the IDE); separate `DepositRequest` and `WithdrawalRequest` instead of one shared request.
- **CNB integration.** I chose CNB's JSON API, and I required the integration to be kept separate from the application's own rate logic: CNB sits behind an interface in its own `integration/cnb` package, so the rest of the application does not know CNB exists. The AI implemented the client to that design; I reviewed it and restructured the packages.
- **Scheduled refresh.** I decided that CNB should not be polled but called once a day, and set the schedule: load at startup, refresh on weekdays at 15:00, half an hour after CNB publishes. The AI implemented the job and its tests to that specification.
- **API usability:** a usable Swagger UI (opens at `/`, editable without extra clicks), Docker Compose for a one-command start, and structured JSON logging.
- **Code cleanup:** comments, formatting and removing redundant code, partly by hand and partly with Junie.
