# ============================================================
# Frontend React - build multi-stage
# Stage 1 (build): Node compila l'app in file statici (dist/)
# Stage 2 (run):   nginx serve i file statici e inoltra /api al backend
# ============================================================

# ---------- 1. BUILD ----------
FROM node:22-alpine AS build
WORKDIR /app

# Dipendenze prima del codice: sfruttiamo la cache di Docker
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# URL del backend "incollato" nel codice durante la build.
# Stringa vuota = chiamate relative (/api/...), gestite dal proxy di nginx.
ARG VITE_API_URL=""
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ---------- 2. RUN ----------
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
