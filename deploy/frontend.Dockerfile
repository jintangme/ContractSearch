FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_AGENT_ENABLED=false
ENV VITE_AGENT_ENABLED=$VITE_AGENT_ENABLED
ENV VITE_RUNTIME_API_BASE=/runtime-api
ENV VITE_LEAN_API_BASE=/lean-api
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY deploy/nginx/demo.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/ /usr/share/nginx/html/
