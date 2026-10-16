# Multicurrency Wallet

A REST API for a single multicurrency account, built with Java 21 and Spring Boot 3.5. You can deposit and withdraw money in CZK or any currency quoted by the Czech National Bank (CNB), and get the total balance converted to a currency of your choice using the official CNB exchange rates.

Balances are kept in memory, as the assignment allows. Rates are fetched from the CNB API, cached, and refreshed once per business day after CNB publishes them. The API is described with OpenAPI and can be tried out in Swagger UI.

## Running the application

### With Docker (recommended)
Requires Docker with Compose. On Windows and macOS, **Docker Desktop must be running.**
```bash
docker compose up --build
```
The first build takes a few minutes while Maven downloads dependencies; later builds are faster. Stop with `Ctrl+C`, or `docker compose down` if started with `-d`.

### Without Docker
Requires Java 21. Maven does not need to be installed; the included Maven Wrapper downloads it.
```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

### Using a different port
If port 8080 is taken, change the **left** number of the port mapping in `compose.yaml`, e.g. `"9090:8080"`, or start without Docker with:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=9090
```
The URLs below then use that port instead of 8080.

### Then open
| URL | What |
|---|---|
| http://localhost:8080/ | Swagger UI: try the API in the browser |
| http://localhost:8080/v3/api-docs | OpenAPI description (JSON; add `.yaml` for YAML) |
| http://localhost:8080/actuator/health | Health, including the state of the exchange rates |

Balances are kept in memory and reset when the application restarts.

## Running the tests
```bash
./mvnw test        # Windows: mvnw.cmd test
```
The tests need no network and no Docker: CNB is replaced by a stub, and time by a controllable clock.

| Level | What it covers | Examples |
|---|---|---|
| **Unit** (plain Java, no Spring) | Money rules, conversion, the in-memory wallet, the rate cache and its schedule, CNB response mapping | `WalletTest`, `WalletServiceTest`, `ExchangeRatesTest`, `ExchangeRateServiceTest`, `CnbDailyRatesResponseTest` |
| **Web layer** (`@WebMvcTest`) | Request validation, JSON format, HTTP status codes and error responses | `WalletControllerTest`, `RatesControllerTest` |
| **HTTP client** (mocked server) | The CNB call: URL, JSON parsing, network and server errors | `CnbExchangeRateProviderTest` |
| **Full application** (`@SpringBootTest`) | The assignment's example workflow end to end, OpenAPI, health, request IDs | `WalletApiIntegrationTest` |

Notable tests:
- `WalletTest.concurrentWithdrawalsNeverOverdraw`: 1,000 parallel withdrawals against one balance (see *Concurrency*).
- `ExchangeRateServiceTest`: when CNB is and isn't called, on weekdays, weekends, before and after 14:30, and when CNB publishes late.
- `WalletServiceTest.assignmentExampleWorkflow`: the example from the assignment with fixed rates (1 EUR = 1.16 USD → 12.80 USD, 11.03 EUR).

## API
Base path: `/api/v1`. All bodies are JSON. Full description in Swagger UI at http://localhost:8080/.

| Method | Path | Body / parameters | Returns |
|---|---|---|---|
| `POST` | `/wallet/deposits` | `{"currency": "USD", "amount": "10.50"}` | New balance of that currency |
| `POST` | `/wallet/withdrawals` | `{"currency": "USD", "amount": "3"}` | New balance of that currency |
| `GET` | `/wallet/balances` | none | Balance of each currency, unconverted |
| `GET` | `/wallet/total` | `?currency=EUR` | All balances converted to that currency |
| `GET` | `/rates` | none | The CNB exchange rates in use (CZK per 1 unit) |

### Example: the workflow from the assignment
```bash
curl -X POST localhost:8080/api/v1/wallet/deposits    -H 'Content-Type: application/json' -d '{"currency": "USD", "amount": "10"}'
# {"currency":"USD","amount":"10.00"}

curl -X POST localhost:8080/api/v1/wallet/deposits    -H 'Content-Type: application/json' -d '{"currency": "EUR", "amount": "5"}'
# {"currency":"EUR","amount":"5.00"}

curl -X POST localhost:8080/api/v1/wallet/withdrawals -H 'Content-Type: application/json' -d '{"currency": "USD", "amount": "3"}'
# {"currency":"USD","amount":"7.00"}

curl 'localhost:8080/api/v1/wallet/total?currency=USD'
# {"currency":"USD","amount":"12.60","ratesValidFor":"2026-10-15","ratesStale":false}

curl 'localhost:8080/api/v1/wallet/total?currency=EUR'
# {"currency":"EUR","amount":"11.25","ratesValidFor":"2026-10-15","ratesStale":false}
```
The totals depend on the day's CNB rates; the values above use the fixing of 15 October 2026.

## Design decisions

### 1. Missing rates (weekends, holidays)
The API always uses the most recent CNB fixing. On weekends, public holidays and before the daily publication at 14:30 Prague time, that is the last business day's fixing, which is what CNB itself returns when asked for the latest rates.

Every total response says which fixing was used:
```json
{ "currency": "USD", "amount": "12.60", "ratesValidFor": "2026-10-15", "ratesStale": false }
```
`ratesValidFor` is the date CNB published the rates for (ISO 8601, `YYYY-MM-DD`). On a Saturday it is the previous Friday's date, so a date earlier than today is expected behaviour, not an error. `GET /api/v1/rates` returns the same date as `validFor`, together with the rates themselves.

### 2. Rate units
CNB quotes some currencies per 100 or 1000 units (e.g. 100 HUF = 6.690 CZK). When rates are loaded, each rate is divided by its unit amount once, and the app stores and uses CZK per **one** unit (1 HUF = 0.0669 CZK) everywhere. CNB's units are powers of ten, so this division is exact. `GET /api/v1/rates` shows rates in this per-unit form.

### 3. CNB unavailable
Rates are cached in memory. They are loaded when the application starts and refreshed by a background job every weekday at 15:00 Prague time, shortly after CNB publishes. CNB is called only when a newer fixing should exist, so normally once per business day. If the background job has not succeeded yet, a request fetches the rates itself. Calls time out after 3 s (connect) and 5 s (read).

If CNB cannot be reached or returns invalid data:
- **Rates are cached:** the API keeps using the last known rates and marks responses with `"ratesStale": true`. CNB is retried at most every 10 minutes; the next successful call clears the flag.
- **No rates cached yet** (CNB down since startup): every endpoint that needs rates, including deposits and withdrawals because supported currencies come from CNB, returns `503` with `"code": "EXCHANGE_RATES_UNAVAILABLE"`.

`ratesStale` therefore means: *these are the newest rates we have, but CNB could not be checked for newer ones.* The health endpoint shows the same information together with the time CNB was last contacted (see *Observability*).

### 4. Money precision
All amounts are `BigDecimal`, never floating point. Balances are kept per currency and never converted when stored.
- **Input:** an amount must be positive and have at most the currency's ISO 4217 decimal places (2 for USD, 0 for JPY; 2 for currencies without a defined value such as XDR). Too many decimal places is rejected with `400 INVALID_AMOUNT` rather than rounded, since rounding would silently change how much money moves. `10` is stored as `10.00`.
- **Conversion:** via CZK at 34 significant digits, without intermediate rounding. The converted balances are summed and the sum is rounded **once**, half-even (banker's rounding), to the target currency's decimal places.
- **JSON:** responses send amounts as decimal strings (`"12.80"`) so JavaScript clients do not lose precision; requests accept strings or numbers.

### 5. Concurrency
Balances are stored in a `ConcurrentHashMap`. A withdrawal checks the balance and subtracts it in one atomic `compute()` call on that currency, so simultaneous withdrawals are processed one after another and can never overdraw: if the balance is too low, the request fails with `422 INSUFFICIENT_FUNDS` and the balance is unchanged. Deposits use the atomic `merge()`. Different currencies do not block each other. `WalletTest.concurrentWithdrawalsNeverOverdraw` runs 1,000 parallel withdrawals of 1.00 USD against a balance of 100.00 USD and checks that exactly 100 succeed; `concurrentDepositsAreNotLost` checks that no parallel deposit is lost.

A total is computed from a copy of the balances: each balance in it is complete and correct, but during simultaneous updates in several currencies it may reflect some of them and not others. Nothing is ever written based on a total, so a global lock was not worth its cost.

### 6. Errors
All errors use RFC 9457 problem details (`application/problem+json`). The rule for clients: **400** means fix your request, **422** means the request is valid but the operation is not allowed, **503** means try again later. Business errors carry a stable `code`:

| Case | Status | `code` | Extra fields |
|---|---|---|---|
| Field missing, amount ≤ 0, currency not 3 uppercase letters, absurd number | 400 | `VALIDATION_ERROR` | `errors`: which field failed and why |
| Too many decimal places (1.005 USD, 1.5 JPY) | 400 | `INVALID_AMOUNT` | |
| Valid format but not quoted by CNB, e.g. `GBX`, or `usd` on `/total` | 422 | `UNSUPPORTED_CURRENCY` | `supportedCurrencies` |
| Withdrawal larger than the balance of that currency | 422 | `INSUFFICIENT_FUNDS` | `currency`, `available`, `requested` |
| CNB down and no cached rates | 503 | `EXCHANGE_RATES_UNAVAILABLE` | |
| Unexpected server error | 500 | `INTERNAL_ERROR` | none |

Example:
```json
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Cannot withdraw 50.00 EUR, the balance is 5.00 EUR",
  "instance": "/api/v1/wallet/withdrawals",
  "code": "INSUFFICIENT_FUNDS",
  "currency": "EUR",
  "available": "5.00",
  "requested": "50.00"
}
```
Malformed JSON, a missing query parameter or an unknown URL return Spring's standard problem response without a `code`. Unexpected errors return `500 INTERNAL_ERROR` without internal details; the cause is logged.

### 7. CZK
CZK is an ordinary wallet currency: it can be deposited, withdrawn and used as the target of a total. CNB quotes every rate *in* CZK and so never lists CZK itself; the app treats it as a fixed rate of 1. All conversions go through CZK (USD → CZK → EUR), so CZK needs no special case elsewhere. CZK is always supported, uses 2 decimal places, and is not listed in `GET /api/v1/rates`, which shows only CNB's published rates.

## Architecture
```
com.example.wallet
├── api/
│   ├── controller/     REST endpoints
│   ├── dto/            request and response bodies
│   └── error/          error handling (problem details, error codes)
├── service/            use cases: deposits, withdrawals, totals; exchange rate cache and its schedule
├── domain/             business objects and rules: exchange rates and conversion, balances, currency decimals
│   └── exception/      business errors
├── storage/            in-memory balance storage
├── integration/cnb/    CNB API client
├── observability/      health check, request logging
└── config/             clock, scheduling, OpenAPI
```
Dependencies point inwards only: `api → service → domain`; `storage` depends only on `domain`. CNB is hidden behind the `ExchangeRateProvider` interface defined in `service`; `integration/cnb` implements it, so the rest of the application does not know CNB exists. Replacing CNB, or the in-memory storage with a database, would not change `domain` or `api`.

Time is injected as a `Clock`, so tests control "now" (e.g. *Saturday*, *before 14:30*) without waiting.

## Configuration
Settings live in `src/main/resources/application.yml`. Any of them can be overridden with an environment variable (dots become `_`, dashes are removed, upper case), e.g. in the `environment:` section of `compose.yaml`.

| Setting | Default | Meaning |
|---|---|---|
| `cnb.base-url` | `https://api.cnb.cz` | CNB API root |
| `spring.http.client.connect-timeout` / `read-timeout` | `3s` / `5s` | Timeouts for CNB calls |
| `exchange-rates.time-zone` | `Europe/Prague` | Time zone of CNB's publication schedule |
| `exchange-rates.publication-time` | `"14:30"` | When CNB publishes a new fixing on weekdays (keep the quotes) |
| `exchange-rates.retry-interval` | `10m` | Wait between CNB attempts while a newer fixing is expected but not available |
| `exchange-rates.refresh.on-startup` | `true` | Load rates when the application starts |
| `exchange-rates.refresh.cron` | `0 0 15 * * MON-FRI` | Background refresh schedule (`-` disables it) |

Example: `EXCHANGERATES_REFRESH_ONSTARTUP=false` disables loading rates at startup.

## Observability

### Structured logging
In Docker the application logs one JSON object per line in the [Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/index.html) (ECS) format, ready for tools like Elasticsearch, Loki or Datadog. Run locally with Maven, it logs readable plain text. The switch is `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` in the `Dockerfile`.

Values are logged as separate fields, not inside the message, so they can be filtered on:
```json
{"@timestamp":"2026-10-16T…","log":{"level":"INFO",…},"message":"Deposit accepted","requestId":"demo-deposit-1","currency":"USD","amount":10.00}
```

### Request IDs
Every API request gets an ID: the client's `X-Request-Id` header if it is safe to log (letters, digits, `-`, at most 64 characters), otherwise a generated UUID. The ID is returned in the `X-Request-Id` response header and added to every log line of that request, so all logs of one call can be found together. Each request also logs one line with `method`, `path`, `status` and `durationMs`. Health checks and Swagger are not logged.

### Health
`GET /actuator/health` reports the application and the exchange rates:
```json
"exchangeRates": { "status": "UP", "details": { "validFor": "2026-10-15", "stale": false, "lastCheckedAt": "2026-10-16T00:04:06Z" } }
```
The health check never calls CNB itself; it reports what the application last saw. `lastCheckedAt` is the last contact with CNB, successful or not. A CNB outage never makes the application `DOWN`, because orchestrators restart unhealthy containers, which cannot fix CNB and would only discard the cached rates. With no rates loaded yet, the component is `UNKNOWN`, which does not change the overall status.

## Known limitations
- **In-memory only.** Balances are lost on restart, and the application cannot run as more than one instance, because each instance would have its own balances. A database behind the `storage` package would solve both.
- **One account, no authentication.** Anyone who can reach the API can use the wallet.
- **No idempotency.** If a client retries a deposit after a timeout, it may be applied twice. A real payment API would accept an `Idempotency-Key` header.
- **No transaction history.** Only current balances are kept.
- **Czech public holidays are unknown.** CNB publishes no fixing on those days, so the app keeps expecting one and retries CNB every 10 minutes until the next business day. A holiday calendar would avoid this.
- **CNB down at startup.** Until the first successful load, every request that needs rates, including deposits and withdrawals, tries CNB again and returns `503` if it fails. Requests wait for each other, up to the timeout each.
- **Health shows details to everyone** (`show-details: always`), including disk information. In production this would be `when-authorized`.
