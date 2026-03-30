import fastifyCookie from "@fastify/cookie";
import fastifyMultipart from "@fastify/multipart";
import fastify from "fastify";
import { initNcmAPI } from "./netease";
import { initQQMusicAPI } from "./qqmusic";
import { initUnblockAPI } from "./unblock";
import { serverHost, serverPort } from "./config";
import { serverLog } from "./logger";

const initStandaloneServer = async () => {
  const server = fastify({
    logger: false,
    trustProxy: true,
    routerOptions: {
      ignoreTrailingSlash: true,
    },
  });

  server.register(fastifyCookie);
  server.register(fastifyMultipart);

  server.get("/healthz", async () => {
    return {
      code: 200,
      message: "ok",
      data: {
        service: "SPlayer API",
        mode: "standalone",
      },
    };
  });

  server.get("/api", async (_, reply) => {
    reply.send({
      name: "SPlayer API",
      description: "SPlayer standalone API service",
      author: "@imsyy",
      list: [
        { name: "NeteaseCloudMusicApi", url: "/api/netease" },
        { name: "UnblockAPI", url: "/api/unblock" },
        { name: "QQMusicAPI", url: "/api/qqmusic" },
      ],
      notes: ["control API 依赖 Electron 桌面窗口，不能在纯云端容器中独立运行"],
    });
  });

  server.register(initNcmAPI, { prefix: "/api" });
  server.register(initUnblockAPI, { prefix: "/api" });
  server.register(initQQMusicAPI, { prefix: "/api" });

  await server.listen({ port: serverPort, host: serverHost });
  serverLog.info(`🌐 Standalone API server started on http://${serverHost}:${serverPort}`);
  return server;
};

void initStandaloneServer().catch((error) => {
  serverLog.error("🚫 Standalone API server failed to start", error);
  process.exit(1);
});
