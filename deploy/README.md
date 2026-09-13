# Deploying Quenaire

One server runs everything: Postgres, the API, the frontend, Ollama for
embeddings, and Caddy for TLS. Generation is hosted, so the server does not need
a GPU.

The region is **ap-south-1 (Mumbai)** because that is what the Information
Security Policy states. Deploying elsewhere makes a document you have signed
untrue; change the policy first if the region changes.

## What the server needs

- 4 GB RAM minimum, 8 GB comfortable. Below 4 GB the JVM and Ollama compete and
  the first upload fails.
- 2 vCPU, 40 GB disk.
- Ubuntu 24.04.
- On AWS Lightsail this is the $24/month 8 GB instance, or $12 for 4 GB.

## Before you start

You need four things, and three of them are yours to get:

| Thing | Where from |
|---|---|
| A domain, e.g. `app.quenaire.in` | Any registrar. An A record pointing at the server's static IP. |
| An AWS account with a Lightsail instance in ap-south-1 | aws.amazon.com |
| An Anthropic API key | console.anthropic.com — this is what pays for generation |
| A Postgres password | `openssl rand -base64 32` on the server |
| Three more secrets for sign-in | `openssl rand -base64 32` each — see `.env.deploy.example` |

Caddy issues the TLS certificate itself; there is nothing to buy or renew.

## Steps

Attach a **static IP** in Lightsail before pointing DNS at it, or the address
changes on reboot and the certificate stops matching.

Open ports 80 and 443 in the Lightsail firewall. Leave 5432 and 8080 closed —
nothing outside the server needs them, and the compose file does not publish
them.

```sh
# On the server
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo usermod -aG docker $USER && newgrp docker

git clone https://github.com/HowSuyash/Quenaire.git
cd Quenaire/quenaire

cp .env.deploy.example .env
nano .env          # fill in every value; DB_PASSWORD from `openssl rand -base64 32`

docker compose -f docker-compose.deploy.yml up -d --build
```

The first run takes 10-15 minutes: it builds both images and pulls the
embedding model. Watch it with:

```sh
docker compose -f docker-compose.deploy.yml logs -f app
```

## Checking it worked

```sh
# From the server: the application's own view
curl -s localhost:8080/api/actuator/health

# From anywhere: the public site
curl -sI https://YOUR_DOMAIN
```

`health` returning `{"status":"UP"}` means Postgres is reachable and migrations
have validated. The actuator is deliberately not reachable from outside — Caddy
returns 404 for `/api/actuator/*`.

## Before real users: authentication is not wired up

**This stack cannot sign anyone in.** It is worth stating plainly rather than
discovering it after a server is running.

Authentication is Keycloak's. The application is an OIDC resource server: it
validates tokens and never sees a password, and the `users` table has no
password column. There is no `/api/auth/login` endpoint -- the login page posts
to one that does not exist. Registration provisions the account in Keycloak,
not here.

This stack has no Keycloak, so there is no way to obtain a token, and every
`/api` call answers 401.

Until that is resolved this deployment is good for a landing page and for
proving the stack runs. It is not ready for a customer. Three ways forward,
in the order they are worth considering:

1. **Add Keycloak to this compose file** and import a realm defining the
   `quenaire` realm, a public `quenaire-frontend` client with this domain in
   its redirect URIs, and a confidential `quenaire-admin` client with the
   `realm-management` roles the provisioning service needs. This is the
   architecture the application already expects. It costs about 512MB of RAM
   and a realm export to get right.
2. **Use a hosted identity provider** -- Auth0, Clerk, WorkOS -- and point
   `app.jwt.issuer-uri` at it. Less to run; a bill, and a dependency on their
   free tier's limits.
3. **Add password authentication to this application.** Smallest change to
   deploy, largest to get right: password storage, reset flows, lockout,
   and the audit obligations that come with holding credentials for a
   security product.

For showing the product to someone before that is settled, use
`docker-compose.demo.yml`, which runs on your own machine behind a temporary
address and enables a local demo session.

## Sign-in

Keycloak runs in the stack and owns the login page; the application never sees a
password. Accounts are kept in Postgres, in their own database created by
`init-db/01-keycloak-db.sh` — which runs **only when Postgres first initialises**.
Adding it to a server whose database already exists does nothing, so create that
database by hand there.

The realm is imported from `deploy/keycloak/quenaire-realm.json` on first start:
two clients, five roles, and the `tenant_id` mapper the backend reads. Its
redirect URI is set from `PUBLIC_URL`, exactly — not a wildcard, because a
redirect URI is where an authorization code gets delivered.

Sign-in is served at `/auth` on the same origin as the app, proxied through the
frontend. There is no second hostname to point at Keycloak.

The first account is created through `/register`, which provisions it in Keycloak
and grants it OWNER. The response says `credentialsProvisioned: true` when that
worked; if it says `false`, the backend could not reach Keycloak's admin API and
the account exists with no way to sign in.

## Things that will bite

**The frontend bakes its API URL in at build time.** `NEXT_PUBLIC_API_URL` is
substituted during compilation, not read at runtime, so changing `PUBLIC_URL`
means `docker compose ... up -d --build frontend`, not a restart.

**Migrations run on startup and are validated, not generated.** If an entity and
its table disagree the application refuses to start rather than starting and
failing later. That is deliberate.

**Email is off.** `MAIL_HEALTH_ENABLED` defaults to false because no SMTP
provider is configured; reviewer invitations will not send. Set `MAIL_HOST` and
friends, then set `MAIL_HEALTH_ENABLED=true` so a broken mail server shows up in
health rather than being assumed working.

**Generation costs money per question.** `ANTHROPIC_MODEL` defaults to Haiku,
which is the cheap end. Answers are grounded in retrieved passages and reviewed
by a human before they go out, so the small model is a reasonable default;
switch to a larger one if answer quality in review is not good enough.

## Backups

`scripts/backup.sh` dumps Postgres. The uploaded documents live in the
`app-storage` volume and are not in that dump — back both up, or an exported
questionnaire loses its sources.

```sh
docker compose -f docker-compose.deploy.yml exec -T postgres \
  pg_dump -U quenaire quenaire | gzip > quenaire-$(date +%F).sql.gz
```

Restoring a database without the matching document volume produces citations
pointing at files that no longer exist. Test a restore before you need one.

## Self-hosted deployments

Set `LLM_PROVIDER=ollama` and `OLLAMA_MODEL`. Nothing then leaves the customer's
network — and generation takes roughly 20 seconds a question on CPU, so a
ninety-question questionnaire is about half an hour. That trade is the point of
the tier, but it should be stated in the contract rather than discovered.
