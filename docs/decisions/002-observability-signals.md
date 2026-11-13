# Observability signals

Status: Proposed in the observability pull request.

Use OpenTelemetry context propagation and OTLP as the transport between the
browser, Spring Boot and a collector. Spring Boot owns HTTP/JVM metrics through
Micrometer and emits JSON console logs through Logback. A Logback appender also
ships logs over OTLP so local traces, metrics and logs can be explored together.
The browser instruments fetch calls to `/api`; a same-origin proxy sends spans
to the collector. Frontend runtime errors may be sent to Sentry when a DSN is
provided, while the app remains usable without that account.

Use Grafana LGTM as a single-container local collector and viewer. It is meant
for development and test, not a production observability deployment. Production
export remains disabled until a collector endpoint is configured. The existing
Kubernetes chart does not install an APM stack. This keeps the application
independent of a specific hosted vendor and avoids running a large monitoring
stack as part of a small application chart.

We considered Datadog and New Relic but they require an external account for
local onboarding. We considered LogRocket instead of Sentry, but the initial
requirement is error reporting; session replay would collect more user data
and needs a separate retention/privacy decision. Alert thresholds are deferred
until the team defines expected traffic and an on-call owner.
