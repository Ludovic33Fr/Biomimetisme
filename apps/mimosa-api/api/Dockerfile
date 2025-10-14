FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install --include=dev
COPY tsconfig.json ./
COPY src ./src
COPY public ./public
RUN npm run build
RUN npm prune --production
ENV NODE_ENV=production
EXPOSE 8080
CMD ["npm", "start"]