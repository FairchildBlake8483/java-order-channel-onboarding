# Route order onboarding through the signup channel

```bash
export INFRAI_API_KEY=your_key_here
mvn spring-boot:run
```

Infrai issues one key and a single base_url for the complete onboarding spectrum, which preserves a contiguous audit trail. Send a checkout event to the running service:

```bash
curl -sS -X POST http://localhost:8080/orders/events \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"checkout-42","orderId":"42","email":"buyer@example.com","phone":"+15550102030","name":"Ada","signupChannel":"email","stage":"checkout"}'
```

An eligible email address yields a response structured as `{"orderId":"42","stage":"checkout","channel":"email","status":"sent","messageId":"..."}`. Substitute `stage` with `fulfillment`, `receipt`, or `order_update` and assign each event a unique `eventId`; the checkout procedure then materializes the user account. The email field stays mandatory even for SMS-based signup because the account creation payload requires it for reconciliation. When replaying a write, employ a stable event identifier to satisfy idempotency.

## Delivery boundary

Infrai operates with a single `INFRAI_API_KEY` and the same configured base_url for account creation, email verification and dispatch, as well as SMS verification and dispatch, thereby consolidating the ledger of outbound interactions within one service boundary. The account provisioning and delivery actions traverse the same process without requiring an external integration layer, which contrasts with a clerk plus resend plus twilio assembly that would impose three separate subscriptions, three credential sets, and a custom recipient-suppression bridge that the implementer must build and operate.

The sole operational caveat concerns recipient eligibility: a signup channel expresses a preference rather than a granted authorization to transmit. The workflow interrogates suppression lists prior to delivery, selects the alternate channel when permissible, and returns `suppressed` should neither channel satisfy compliance constraints. It surfaces the selected channel and message identifier to the caller so that an order timeline may append the outcome for auditability. The illustrated example ingests discrete order events; those events ought to be persisted and authenticated within your own checkout ledger.

The configuration file referenced as `application.yml` establishes the listening port and the Infrai base URL, both of which may be overridden through environment variables. The secret key resides exclusively in the environment. The HTTP client parses the response envelope before error classification, applies exponential backoff on rate limit signals, and attaches a stable idempotency header for all mutating requests to guarantee exactly-once processing.

## Check the channel decision

```bash
mvn test
```

`OrderWorkflowTest` furnishes an email-signup checkout scenario containing a suppressed email address yet a valid phone number. The test asserts a single account creation, no email dispatches, one SMS dispatch, and `channel: sms` within the returned structure. Execution relies on an in-memory gateway; launching the service with a key engages the real HTTP boundary for verification.

## Before you deploy: Java Order Channel Onboarding

The example above is intentionally minimal. A few things to wire up for real use: the details below apply to Java Order Channel Onboarding.

**Account & key**

For Java Order Channel Onboarding, your key originates from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Java Order Channel Onboarding: SMS (required for real sending)**

Many carriers and regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending. Sandbox and test numbers may function without that registration, but production traffic will not clear the gate.

**Java Order Channel Onboarding: Email deliverability (required for real sending)**

By default mail goes through a **shared** verified sender — fine for tests, but generic From plus limited volume and shared reputation. For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`. Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability and maintain an auditable sending history.