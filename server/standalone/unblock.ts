import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import axios from "axios";
import { serverLog } from "./logger";
import type { SongMatchInfo, SongUrlResult } from "./types";
import getKuwoSongUrl from "./unblock/kuwo.ts";
import getBodianSongUrl from "./unblock/bodian.ts";
import getGequbaoSongUrl from "./unblock/gequbao.ts";

const DIRECT_NETEASE_UNLOCK_BASE_URL = "https://music-api.gdstudio.xyz";

const getNeteaseSongUrl = async (id: number | string): Promise<SongUrlResult> => {
  try {
    if (!id) return { code: 404, url: null };
    const result = await axios.get(`${DIRECT_NETEASE_UNLOCK_BASE_URL}/api.php`, {
      params: { types: "url", id },
    });
    const songUrl = result.data.url;
    serverLog.log("🔗 NeteaseSongUrl URL:", songUrl);
    return { code: 200, url: songUrl };
  } catch (error) {
    serverLog.error("❌ Get NeteaseSongUrl Error:", error);
    return { code: 404, url: null };
  }
};

const buildMatchInfo = (query: { [key: string]: string }): SongMatchInfo => {
  let songName = query.songName || "";
  let artist = query.artist || "";
  if (!songName && query.keyword) {
    const lastIdx = query.keyword.lastIndexOf("-");
    if (lastIdx > 0) {
      songName = query.keyword.slice(0, lastIdx).trim();
      artist = artist || query.keyword.slice(lastIdx + 1).trim();
    } else {
      songName = query.keyword.trim();
    }
  }
  return { keyword: query.keyword || "", songName, artist };
};

export const initUnblockAPI = async (fastify: FastifyInstance) => {
  fastify.get("/unblock", (_, reply) => {
    reply.send({
      name: "UnblockAPI",
      description: "SPlayer UnblockAPI service",
      author: "@imsyy",
      content:
        "部分接口采用 @939163156 by GD音乐台(music.gdstudio.xyz)，仅供本人学习使用，不可传播下载内容，不可用于商业用途。",
    });
  });

  fastify.get(
    "/unblock/netease",
    async (
      req: FastifyRequest<{ Querystring: { [key: string]: string } }>,
      reply: FastifyReply,
    ) => {
      const { id } = req.query;
      const result = await getNeteaseSongUrl(id);
      return reply.send(result);
    },
  );

  fastify.get(
    "/unblock/kuwo",
    async (
      req: FastifyRequest<{ Querystring: { [key: string]: string } }>,
      reply: FastifyReply,
    ) => {
      return reply.send(await getKuwoSongUrl(buildMatchInfo(req.query)));
    },
  );

  fastify.get(
    "/unblock/bodian",
    async (
      req: FastifyRequest<{ Querystring: { [key: string]: string } }>,
      reply: FastifyReply,
    ) => {
      return reply.send(await getBodianSongUrl(buildMatchInfo(req.query)));
    },
  );

  fastify.get(
    "/unblock/gequbao",
    async (
      req: FastifyRequest<{ Querystring: { [key: string]: string } }>,
      reply: FastifyReply,
    ) => {
      return reply.send(await getGequbaoSongUrl(buildMatchInfo(req.query)));
    },
  );

  serverLog.info("🌐 Register UnblockAPI successfully");
};
