# 服务器端 API 部署

这份部署方案只覆盖能在单独服务器上稳定运行的 API 层：

- `/api/netease`
- `/api/unblock`
- `/api/qqmusic`

`/api/control` 依赖 Electron 桌面进程里的 `mainWindow`，本质上是本地桌面控制能力，不能像普通 HTTP 服务一样直接丢进云端容器独立运行。若一定要远程控制桌面播放器，需要保留桌面端常驻运行，或者额外做一个桌面代理层。

## 代码组织

建议把云端 API 代码放在仓库内的 `server/standalone/`，与 Electron 主进程彻底分开：

- `server/standalone/index.ts`
- `server/standalone/config.ts`
- `server/standalone/logger.ts`
- `server/standalone/netease.ts`
- `server/standalone/unblock.ts`
- `server/standalone/qqmusic.ts`

现有 Electron 目录中的实现保留不动，桌面版继续使用本地 App + WebSocket + `control` 这条链路。

## 环境变量

最少只需要这些变量：

- `PORT` 或 `VITE_SERVER_PORT`，默认 `25884`
- `HOST`，默认 `0.0.0.0`
- `AMLL_DB_SERVER`，默认 `https://amlldb.bikonoo.com/ncm-lyrics/%s.ttml`

如果你自己的环境里已经有可用的 TTML 源，直接把 `AMLL_DB_SERVER` 改成自己的地址。

## 本地运行

先安装依赖，然后直接启动：

```bash
pnpm install
pnpm exec tsx server/standalone/index.ts
```

启动后默认监听 `http://0.0.0.0:25884`，健康检查地址是 `http://127.0.0.1:25884/healthz`。

如果你本机 `25884` 端口跑的是桌面版内建服务，而不是 `server/standalone/`，现在同样也支持 `/healthz`。两者的区别主要看返回内容：

- standalone 会返回 `mode: "standalone"`
- Electron 内建服务会返回 `mode: "electron"`，并带上 `control: true`

## Docker 部署

仓库里已经放了最小可用的 Docker 目录：

- [Dockerfile](/deploy/server/Dockerfile)
- [docker-compose.yml](/deploy/server/docker-compose.yml)
- [nginx.conf](/deploy/server/nginx.conf)

### 构建镜像

```bash
docker build -f deploy/server/Dockerfile -t splayer-api .
```

### 直接运行

```bash
docker run -d \
  --name splayer-api \
  -e HOST=0.0.0.0 \
  -e PORT=25884 \
  -e AMLL_DB_SERVER="https://amlldb.bikonoo.com/ncm-lyrics/%s.ttml" \
  -p 25884:25884 \
  splayer-api
```

### Compose + Nginx

```bash
cd deploy/server
docker compose up -d
```

这个 compose 会启动两个容器：

- `api`：真正的 Fastify API 服务
- `nginx`：对外统一反代入口

外部访问仍然是 `http://你的服务器:25884/api/...`

## Nginx 反代

如果你只想把现有 API 容器挂到 Nginx 后面，核心配置就是：

```nginx
location /api/ {
  proxy_pass http://api:25884;
  proxy_http_version 1.1;
  proxy_set_header Host $host;
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  proxy_set_header X-Forwarded-Proto $scheme;
}
```

如果你未来要把 HTTPS 也接上，只需要在 Nginx 外层再加一层证书和 `listen 443 ssl`，API 容器本身不需要改。

## 取舍

- `netease / unblock / qqmusic` 可以直接做成独立服务
- `control` 不适合云端独立部署
- 如果你要真正远程控制桌面播放器，建议额外做一个桌面代理，而不是把 Electron 主进程硬塞进 Docker
