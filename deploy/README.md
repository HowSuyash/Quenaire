# Deploying ComplyAI

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
| A domain, e.g. `app.complyai.in` | Any registrar. An A record pointing at the server's static IP. |
| An AWS account with a Lightsail instance in ap-south-1 | aws.amazon.com |
| An Anthropic API key | console.anthropic.com — this is what pays for generation |
| A Postgres password | `openssl rand -base64 32` on the server |

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

git clone https://github.com/HowSuyash/ComplyAI.git
cd ComplyAI/complyai

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
  pg_dump -U complyai complyai | gzip > complyai-$(date +%F).sql.gz
```

Restoring a database without the matching document volume produces citations
pointing at files that no longer exist. Test a restore before you need one.

## Self-hosted deployments

Set `LLM_PROVIDER=ollama` and `OLLAMA_MODEL`. Nothing then leaves the customer's
network — and generation takes roughly 20 seconds a question on CPU, so a
ninety-question questionnaire is about half an hour. That trade is the point of
the tier, but it should be stated in the contract rather than discovered.
