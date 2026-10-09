FROM node:22-bookworm-slim
WORKDIR /app
COPY contract-runtime-service/package*.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY contract-runtime-service/src/ ./src/
USER node
CMD ["node", "src/index.js"]
