# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# Vite embeds env variables at build time — pass the public Grafana URL so the
# admin sidebar link points to the right host instead of localhost.
ARG VITE_GRAFANA_URL=http://localhost:3000
ENV VITE_GRAFANA_URL=$VITE_GRAFANA_URL

RUN npm run build

# ─── Stage 2: Serve ──────────────────────────────────────────────────────────
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

COPY --from=build /app/dist .
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
