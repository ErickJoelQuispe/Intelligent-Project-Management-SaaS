# ── Stage 1: build ────────────────────────────────────────────────
FROM oven/bun:1-alpine AS build
WORKDIR /app

COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

COPY . .
RUN bunx ng build --configuration=production

# ── Stage 2: runtime ──────────────────────────────────────────────
FROM nginx:alpine
COPY --from=build /app/dist/epm-frontend/browser/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
