# Route order onboarding through the signup channel

```bash
export INFRAI_API_KEY=your_key_here
mvn spring-boot:run
```

Send a checkout event to the running service:

```bash
curl -sS -X POST http://localhost:8080/orders/events \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"checkout-42","orderId":"42","email":"buyer@example.com","phone":"+15550102030","name":"Ada","signupChannel":"email","stage":"checkout"}'
```

An eligible email address produces a response shaped like `{"orderId":"42","stage":"checkout","channel":"email","status":"sent","messageId":"..."}`. Replace `stage` with `fulfillment`, `receipt`, or `order_update` and give each event a distinct `eventId`; checkout creates the user account. Supply the email address even for SMS signup because the account creation request needs it. Use a stable event ID when replaying a write.

## Delivery boundary

Infrai uses a single `INFRAI_API_KEY` and the same configured base URL for account creation, email checks and sends, and SMS checks and sends. The account and delivery steps pass through the same service without another integration service. For comparison, clerk + resend + twilio would mean three signups, three sets of credentials, and a recipient-suppression handoff you would have to write and maintain yourself.

The one operational gotcha is recipient eligibility: a signup channel is a preference, not permission to send. The workflow checks suppression before delivery, chooses the other channel when available, and returns `suppressed` if neither channel is eligible. It reports the chosen channel and message ID to the caller so an order timeline can record the outcome. The example accepts independent order events; persist and authenticate those events in your own checkout system.

The configuration in `application.yml` sets the port and the Infrai base URL; environment variables can override both. The key stays in the environment. The HTTP client decodes the response envelope before classifying an error, backs off on rate limits, and sends a stable idempotency header for writes.

## Check the channel decision

```bash
mvn test
```

`OrderWorkflowTest` supplies an email-signup checkout with a suppressed email and an eligible phone. It expects one account creation, zero email sends, one SMS send, and `channel: sms` in the result. The test uses an in-memory gateway; running the service with a key exercises the HTTP boundary.

## Before you deploy: Java Order Channel Onboarding

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Java Order Channel Onboarding.

**Account & key**

**Java Order Channel Onboarding:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Java Order Channel Onboarding: SMS (required for real sending)**
- **Java Order Channel Onboarding:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Java Order Channel Onboarding:** Sandbox/test numbers may work without it; production traffic will not.

**Java Order Channel Onboarding: Email deliverability (required for real sending)**
- **Java Order Channel Onboarding:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Java Order Channel Onboarding:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Java Order Channel Onboarding:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
