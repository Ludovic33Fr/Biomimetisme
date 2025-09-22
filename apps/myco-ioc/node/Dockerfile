FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY src ./src
CMD ["node","src/index.js"]  # ou ts-node si tu compiles TS → JS
