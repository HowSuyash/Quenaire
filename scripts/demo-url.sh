#!/bin/sh
# Brings the demo to a usable state and prints its public address.
#
# Cloudflare assigns a new hostname on every run and only announces it in the
# tunnel's log, so nothing can be configured with it in advance. Keycloak will
# not redirect to a host it has not been told about, and it accepts no wildcard
# in the host part of a redirect URI -- only in the path -- so the address has
# to be registered once it is known.
set -e

# Git Bash rewrites arguments that look like Unix paths into Windows ones, so
# /opt/keycloak/bin/kcadm.sh became C:/Program Files/Git/opt/... and the exec
# failed. Ignored on Linux.
MSYS_NO_PATHCONV=1
export MSYS_NO_PATHCONV

compose="docker compose -f docker-compose.demo.yml"
kcadm=/opt/keycloak/bin/kcadm.sh
realm=quenaire
client=quenaire-frontend
local_url=http://localhost:3000

printf 'waiting for the tunnel to be assigned an address'
url=''
i=0
while [ $i -lt 60 ]; do
    # The last one, not the first: Cloudflare tears a quick tunnel down after a
    # while and cloudflared reconnects with a new address, so the log
    # accumulates them and the oldest is dead.
    url=$($compose logs cloudflared 2>/dev/null \
          | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' \
          | tail -1)
    [ -n "$url" ] && break
    printf '.'
    i=$((i + 1))
    sleep 2
done

if [ -z "$url" ]; then
    printf '\n\nNo address yet. Check the tunnel:\n  %s logs cloudflared\n' "$compose"
    exit 1
fi

printf '\nregistering %s with Keycloak' "$url"

# kcadm runs inside the container, so the admin password never leaves the host
# and Keycloak is never exposed for administration.
if ! $compose exec -T keycloak "$kcadm" config credentials \
        --server http://localhost:8080/auth \
        --realm master \
        --user "${KEYCLOAK_ADMIN_USERNAME:-admin}" \
        --password "${KEYCLOAK_ADMIN_PASSWORD:-admin}" >/dev/null; then
    printf '\n\nCould not sign in to Keycloak to register the address.\n'
    exit 1
fi

id=$($compose exec -T keycloak "$kcadm" get clients \
        -r "$realm" -q clientId="$client" --fields id --format csv --noquotes \
     | tr -d '\r' | head -1)

if [ -z "$id" ]; then
    printf '\n\nCould not find the %s client in realm %s.\n' "$client" "$realm"
    exit 1
fi

# Exactly these two, never a wildcard: a redirect URI is where an authorization
# code gets delivered. localhost is here because the demo publishes port 3000 as
# well, which is how it stays usable when the tunnel drops.
if ! $compose exec -T keycloak "$kcadm" update "clients/$id" \
        -r "$realm" \
        -s "redirectUris=[\"$url/*\",\"$local_url/*\"]" \
        -s "webOrigins=[\"$url\",\"$local_url\"]" >/dev/null; then
    printf '\n\nCould not register %s as a redirect address.\n' "$url"
    exit 1
fi

printf '\n\n  %s\n  %s\n\n' "$url" "$local_url"
printf 'The first is temporary: it changes on restart and stops working when you\n'
printf 'stop the stack. The second works on this machine for as long as the\n'
printf 'stack is up.\n'
