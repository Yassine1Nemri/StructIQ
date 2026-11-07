FROM node:24-bookworm-slim AS test
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run lint && npm test && npm run build

FROM nginxinc/nginx-unprivileged:1.28.2-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=test /app/dist /usr/share/nginx/html
EXPOSE 8080
