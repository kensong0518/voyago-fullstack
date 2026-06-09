# syntax=docker/dockerfile:1.7

# ============ build ============
FROM node:20-alpine AS build
WORKDIR /app

# Vite 需要的環境變數在 build 階段就 baked-in
ARG VITE_API_BASE=/api
ARG VITE_GOOGLE_CLIENT_ID=
ENV VITE_API_BASE=$VITE_API_BASE
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID

# 先 COPY lockfile 確保依賴層可重用；npm ci 保證鎖檔一致
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ============ serve ============
FROM nginx:alpine

# 移除預設 server，套用我們強化過的 conf
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html

# nginx:alpine 預設已是 non-root；只暴露 80
EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1
