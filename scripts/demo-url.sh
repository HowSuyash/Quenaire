#!/bin/sh
# Prints the public address of a running demo tunnel.
#
# Cloudflare assigns the address after the tunnel connects, and only announces
# it in the log, so there is nothing to read from configuration.
set -e

compose="docker compose -f docker-compose.demo.yml"

printf 'waiting for the tunnel to be assigned an address'
i=0
while [ $i -lt 60 ]; do
    url=$($compose logs cloudflared 2>/dev/null \
          | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' \
          | head -1)
    if [ -n "$url" ]; then
        printf '\n\n  %s\n\n' "$url"
        printf 'That address is temporary: it changes on restart and stops working\n'
        printf 'when you stop the stack. Fine for a scheduled demo, not for a link\n'
        printf 'you put on a website.\n'
        exit 0
    fi
    printf '.'
    i=$((i + 1))
    sleep 2
done

printf '\n\nNo address yet. Check the tunnel:\n  %s logs cloudflared\n' "$compose"
exit 1
