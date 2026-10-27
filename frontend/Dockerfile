# 构建阶段：容器内装依赖并打包，服务器不需要装 Node
FROM node:24-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# 运行阶段：Nginx 托管静态文件，同时反代 /api 与 /ws
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
