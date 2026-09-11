#!/bin/sh
# Brings the demo to a usable state and prints its address.
#
# Cloudflare assigns a new hostname on every run and only announces it in the
# tunnel's log, so nothing can be configured with it ahead of time. Keycloak will
# not redirect to a host it has not been told about -- and it does not accept a
# wildcard in the host part of a redirect URI, only in the path -- so the address
# has to be registered once it is known.
set -e

# Git Bash rewrites arguments that look like Unix paths into Windows ones, so
# /opt/keycloak/bin/kcadm.sh inside the container became C:/Program Files/Git/...
# and the exec failed. Unused elsewhere.
MSYS_NO_PATHCONV=1
export MSYS_NO_PATHCONV

compose="docker compose -f docker-compose.demo.yml"
kcadm="/opt/keycloak/bin/kcadm.sh"
realm=quenaire
client=quenaire-frontend

printf 'waiting for the tunnel to be assigned an address'
url=''
i=0
while [ $i -lt 60 ]; do
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
$compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://localhost:8080/auth \
    --realm master \
    --user "${KEYCLOAK_ADMIN_USERNAME:-admin}" \
    --password "${KEYCLOAK_ADMIN_PASSWORD:-admin}" >/dev/null 2>&1

id=$($compose exec -T keycloak /opt/keycloak/bin/kcadm.sh get clients \
        -r "$realm" -q clientId="$client" --fields id --format csv --noquotes 2>/dev/null \
     | tr -d '\r' | head -1)

if [ -z "$id" ]; then
    printf '\n\nCould not find the %s client in realm %s.\n' "$client" "$realm"
    exit 1
fi

$compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update "clients/$id" \
    -r "$realm" \
    -s "redirectUris=[\"$url/*\"]" \
    -s "webOrigins=[\"$url\"]" >/dev/null 2>&1

printf '\n\n  %s\n\n' "$url"
printf 'Temporary: the address changes on restart and stops working when you\n'
printf 'stop the stack. Fine for a scheduled demo, not for a link on a website.\n'
