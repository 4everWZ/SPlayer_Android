import { pathCase } from "change-case";
import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import NeteaseCloudMusicApi from "@neteasecloudmusicapienhanced/api";
import { defaultAMLLDbServer } from "./config";
import { serverLog } from "./logger";

export const initNcmAPI = async (fastify: FastifyInstance) => {
  fastify.get("/netease", (_, reply) => {
    reply.send({
      name: "@neteaseapireborn/api",
      description: "网易云音乐 API Enhanced",
      author: "@MoeFurina",
      license: "MIT",
      url: "https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced",
    });
  });

  const dynamicHandler = async (req: FastifyRequest, reply: FastifyReply) => {
    const { "*": requestPath } = req.params as { "*": string };
    const routerName = Object.keys(NeteaseCloudMusicApi).find((key) => {
      if (typeof (NeteaseCloudMusicApi as Record<string, unknown>)[key] !== "function") {
        return false;
      }
      return pathCase(key) === requestPath || key === requestPath;
    });

    if (!routerName) {
      return reply.status(404).send({ error: "API not found" });
    }

    const neteaseApi = (
      NeteaseCloudMusicApi as unknown as Record<string, (params: unknown) => Promise<any>>
    )[routerName];
    serverLog.log("🌐 Request NcmAPI:", requestPath);

    try {
      const result = await neteaseApi({
        ...(req.query as Record<string, unknown>),
        ...(req.body as Record<string, unknown>),
        cookie: req.cookies,
      });
      return reply.send(result.body);
    } catch (error: unknown) {
      serverLog.error("❌ NcmAPI Error:", error);
      if (typeof error === "object" && error) {
        const err = error as { status: number; body: unknown; message?: string };
        if ([400, 301].includes(err.status)) {
          return reply.status(err.status).send(err.body);
        }
        return reply
          .status(500)
          .send(err.body || { error: err.message || "Internal Server Error" });
      }
      return reply.status(500).send({ error: String(error) });
    }
  };

  fastify.get("/netease/*", dynamicHandler);
  fastify.post("/netease/*", dynamicHandler);

  fastify.get(
    "/netease/lyric/ttml",
    async (req: FastifyRequest<{ Querystring: { id: string } }>, reply: FastifyReply) => {
      const { id } = req.query;
      if (!id) {
        return reply.status(400).send({ error: "id is required" });
      }
      const url = defaultAMLLDbServer.replace("%s", String(id));
      try {
        const response = await fetch(url);
        if (response.status !== 200) {
          return reply.send(null);
        }
        return reply.send(await response.text());
      } catch (error) {
        serverLog.error("❌ TTML Lyric Fetch Error:", error);
        return reply.send(null);
      }
    },
  );

  serverLog.info("🌐 Register NcmAPI successfully");
};
