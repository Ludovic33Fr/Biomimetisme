FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY tsconfig.json ./
COPY src ./src
COPY public ./public
RUN npm run build
ENV NODE_ENV=production
EXPOSE 8080
CMD ["npm", "start"]