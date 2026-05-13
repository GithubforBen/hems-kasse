# syntax=docker/dockerfile:1.7
#
# Nuxt 3 frontend — multi-stage build.
#
#   docker build -t schulkasse-frontend frontend/
#   docker run --rm -p 3000:3000 \
#     -e NUXT_PUBLIC_API_BASE=http://localhost:8080 \
#     schulkasse-frontend
#

# ----------------------------------------------------------------------
# 1) Build stage — install deps with pnpm and run `nuxt build`.
# ----------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Activate pnpm via corepack, pinning version from package.json packageManager if set.
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate

# Install deps first for caching.
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

# Now bring in the rest and build. Build is fully static-output-able,
# but we use the Nitro node-server preset so the runtime can serve SPA + future server routes.
COPY . .
RUN pnpm build

# ----------------------------------------------------------------------
# 2) Runtime stage — only the .output is needed; no dev deps, no pnpm.
# ----------------------------------------------------------------------
FROM node:22-alpine AS runtime
WORKDIR /app

RUN addgroup -S -g 1001 nuxt && adduser -S -G nuxt -u 1001 nuxt

COPY --from=build /app/.output /app/.output

ENV NODE_ENV=production \
    HOST=0.0.0.0 \
    PORT=3000 \
    NUXT_PUBLIC_API_BASE=http://localhost:8080

EXPOSE 3000
USER nuxt

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1:3000/ >/dev/null 2>&1 || exit 1

CMD ["node", ".output/server/index.mjs"]
