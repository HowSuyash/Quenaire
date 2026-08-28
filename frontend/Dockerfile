# syntax=docker/dockerfile:1

# ---- dependencies -----------------------------------------------------------
FROM node:20-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

# ---- build ------------------------------------------------------------------
FROM node:20-alpine AS builder

WORKDIR /app

COPY --from=deps /app/node_modules node_modules/
COPY . .

# Next compiles rewrites into routes-manifest.json during the build, so the
# rewrite destination is fixed here rather than read at runtime. That is fine:
# this is the internal service address, identical in every deployment. The
# public hostname is what must not be baked in, and it is not -- the browser
# calls whatever origin served it.
ARG BACKEND_URL=http://app:8080
ENV BACKEND_URL=$BACKEND_URL

# Offline fixtures are compiled out unless this is "true". Never set it here:
# the guards exist so a production build cannot serve invented answers.
ENV NEXT_PUBLIC_USE_MOCK_DATA=false
ENV NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# ---- runtime ----------------------------------------------------------------
FROM node:20-alpine

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000

RUN addgroup -g 1001 -S nodejs \
 && adduser -u 1001 -S nextjs -G nodejs

COPY --from=builder /app/public public/
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static .next/static/

USER nextjs

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD node -e "fetch('http://localhost:3000/').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

CMD ["node", "server.js"]
