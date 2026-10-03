# syntax=docker/dockerfile:1
# Production image for the ApnaLaundry website (Next.js standalone), modelled
# on the courses admin Dockerfile. Runs the Next server; its route handler
# proxies /api/laundry/* to the backend (BACKEND_URL, a RUNTIME env).
FROM node:22-alpine AS deps
WORKDIR /app
COPY package*.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* are inlined at BUILD time — a change needs a rebuild, not a
# restart. BACKEND_URL is NOT here: it is read at runtime by the proxy route.
ARG NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID
ARG NEXT_PUBLIC_CLEVERTAP_REGION
ARG NEXT_PUBLIC_PINNED_TODAY
ARG NEXT_PUBLIC_PINNED_NOW_MINUTES
ENV NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID=$NEXT_PUBLIC_CLEVERTAP_ACCOUNT_ID
ENV NEXT_PUBLIC_CLEVERTAP_REGION=$NEXT_PUBLIC_CLEVERTAP_REGION
ENV NEXT_PUBLIC_PINNED_TODAY=$NEXT_PUBLIC_PINNED_TODAY
ENV NEXT_PUBLIC_PINNED_NOW_MINUTES=$NEXT_PUBLIC_PINNED_NOW_MINUTES
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
USER node
EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0
# BACKEND_URL is supplied at runtime (compose env), e.g. http://backend:5001
CMD ["node", "server.js"]
