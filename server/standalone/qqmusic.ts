import type { FastifyInstance, FastifyReply, FastifyRequest } from "fastify";
import axios from "axios";
import { decryptQrc } from "../../electron/server/qqmusic/qrc.ts";
import { serverLog } from "./logger";

const QM_API_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";

const QM_HEADERS = {
  "Content-Type": "application/json",
  "Accept-Encoding": "gzip",
  "User-Agent": "okhttp/3.14.9",
  Cookie: "tmeLoginType=-1;",
};

const getCommonParams = () => ({
  ct: 11,
  cv: "1003006",
  v: "1003006",
  os_ver: "15",
  phonetype: "24122RKC7C",
  tmeAppID: "qqmusiclight",
  nettype: "NETWORK_WIFI",
  udid: "0",
});

let sessionCache: {
  uid?: string;
  sid?: string;
  userip?: string;
  expireTime?: number;
} = {};

async function initSession(): Promise<void> {
  if (sessionCache.uid && sessionCache.expireTime && Date.now() < sessionCache.expireTime) {
    return;
  }

  try {
    const response = await axios.post(
      QM_API_URL,
      {
        comm: getCommonParams(),
        request: {
          method: "GetSession",
          module: "music.getSession.session",
          param: { caller: 0, uid: "0", vkey: 0 },
        },
      },
      { headers: QM_HEADERS },
    );

    const data = response.data;
    if (data.code === 0 && data.request?.code === 0) {
      const session = data.request.data.session;
      sessionCache = {
        uid: session.uid,
        sid: session.sid,
        userip: session.userip,
        expireTime: Date.now() + 3600000,
      };
      serverLog.log("🔑 QQ 音乐会话初始化成功");
    }
  } catch {
    serverLog.warn("⚠️ QQ 音乐会话初始化失败，将使用默认参数");
  }
}

async function qmRequest(method: string, module: string, param: Record<string, any>): Promise<any> {
  await initSession();

  const comm = {
    ...getCommonParams(),
    ...(sessionCache.uid ? { uid: sessionCache.uid } : {}),
    ...(sessionCache.sid ? { sid: sessionCache.sid } : {}),
    ...(sessionCache.userip ? { userip: sessionCache.userip } : {}),
  };

  const response = await axios.post(
    QM_API_URL,
    {
      comm,
      request: { method, module, param },
    },
    { headers: QM_HEADERS },
  );

  const data = response.data;
  if (data.code !== 0 || data.request?.code !== 0) {
    throw new Error(`QM API 错误: ${data.code || data.request?.code}`);
  }

  return data.request.data;
}

const getQQMusicLyric = async (
  songId: number,
  songName: string = "",
  singerName: string = "",
  albumName: string = "",
  duration: number = 0,
) => {
  try {
    const b64encode = (str: string) => Buffer.from(str, "utf8").toString("base64");
    const param = {
      albumName: b64encode(albumName),
      crypt: 1,
      ct: 19,
      cv: 2111,
      interval: duration,
      lrc_t: 0,
      qrc: 1,
      qrc_t: 0,
      roma: 1,
      roma_t: 0,
      singerName: b64encode(singerName),
      songID: songId,
      songName: b64encode(songName),
      trans: 1,
      trans_t: 0,
      type: 0,
    };

    const response = await qmRequest(
      "GetPlayLyricInfo",
      "music.musichallSong.PlayLyricInfo",
      param,
    );

    const result: {
      code: number;
      lrc?: string;
      qrc?: string;
      trans?: string;
      roma?: string;
      message?: string;
    } = { code: 200 };

    const lyric = response.lyric;
    if (lyric && lyric.length > 0) {
      try {
        result.qrc = decryptQrc(lyric);
      } catch (error: any) {
        serverLog.error("QRC 歌词解密失败:", error.message);
      }
    }

    if (response.qrc_t === 0 && lyric) {
      try {
        result.lrc = decryptQrc(lyric);
      } catch {
        // ignore
      }
    } else {
      try {
        const lrcResponse = await qmRequest(
          "GetPlayLyricInfo",
          "music.musichallSong.PlayLyricInfo",
          {
            ...param,
            qrc: 0,
            qrc_t: 0,
          },
        );
        if (lrcResponse.lyric && lrcResponse.lyric.length > 0) {
          result.lrc = decryptQrc(lrcResponse.lyric);
        }
      } catch {
        // ignore
      }
    }

    const trans = response.trans;
    if (trans && trans.length > 0) {
      try {
        result.trans = decryptQrc(trans);
      } catch {
        // ignore
      }
    }

    const roma = response.roma;
    if (roma && roma.length > 0) {
      try {
        result.roma = decryptQrc(roma);
      } catch {
        // ignore
      }
    }

    return result;
  } catch (error: any) {
    serverLog.error("QQ 音乐歌词获取失败:", error.message);
    return {
      code: 500,
      message: error.message || "服务器错误",
    };
  }
};

const searchQQMusic = async (keyword: string, page: number = 1, pageSize: number = 20) => {
  try {
    const param = {
      search_id: String(
        Math.floor(Math.random() * 20) * 18014398509481984 +
          Math.floor(Math.random() * 4194304) * 4294967296 +
          (Date.now() % 86400000),
      ),
      remoteplace: "search.android.keyboard",
      query: keyword,
      search_type: 0,
      num_per_page: pageSize,
      page_num: page,
      highlight: 0,
      nqc_flag: 0,
      page_id: 1,
      grp: 1,
    };

    const response = await qmRequest(
      "DoSearchForQQMusicLite",
      "music.search.SearchCgiService",
      param,
    );
    const songList = response.body?.item_song || [];
    const songs = songList.map((song: any) => ({
      id: String(song.id),
      mid: song.mid,
      name: song.title,
      artist:
        song.singer
          ?.map((s: any) => s.name)
          .filter(Boolean)
          .join(" / ") || "未知歌手",
      album: song.album?.name || "",
      duration: (song.interval || 0) * 1000,
    }));

    return {
      code: 200,
      songs,
      total: response.meta?.sum || songs.length,
    };
  } catch (error: any) {
    serverLog.error("QQ 音乐搜索失败:", error);
    return {
      code: 500,
      message: error.message || "服务器错误",
    };
  }
};

export const initQQMusicAPI = async (fastify: FastifyInstance) => {
  initSession().catch(() => {});

  fastify.get("/qqmusic", (_, reply) => {
    reply.send({
      name: "QQMusicAPI",
      description: "QQ 音乐歌词获取 API（基于 LDDC 项目）",
      author: "@imsyy",
      routes: [
        {
          path: "/api/qqmusic/lyric",
          method: "GET",
          params: {
            id: "歌曲 ID（必须）",
            name: "歌曲名称（推荐）",
            artist: "歌手名称（推荐）",
            album: "专辑名称（可选）",
            duration: "时长秒数（可选）",
          },
          description: "获取歌词（LRC 和逐字歌词）",
        },
        {
          path: "/api/qqmusic/search",
          method: "GET",
          params: {
            keyword: "搜索关键词",
            page: "页码（可选，默认1）",
            pageSize: "每页数量（可选，默认20）",
          },
          description: "搜索歌曲",
        },
        {
          path: "/api/qqmusic/match",
          method: "GET",
          params: {
            keyword: "搜索关键词（歌曲名-歌手）",
          },
          description: "模糊匹配获取歌词（搜索并返回第一个匹配的歌词）",
        },
      ],
    });
  });

  fastify.get(
    "/qqmusic/lyric",
    async (
      req: FastifyRequest<{
        Querystring: {
          id: string;
          name?: string;
          artist?: string;
          album?: string;
          duration?: string;
        };
      }>,
      reply: FastifyReply,
    ) => {
      const { id, name = "", artist = "", album = "", duration = "0" } = req.query;
      if (!id) {
        return reply.status(400).send({ code: 400, message: "id 参数是必须的" });
      }

      const songId = parseInt(id, 10);
      if (Number.isNaN(songId)) {
        return reply.status(400).send({ code: 400, message: "id 必须是有效的数字" });
      }

      return reply.send(
        await getQQMusicLyric(songId, name, artist, album, parseInt(duration, 10) || 0),
      );
    },
  );

  fastify.get(
    "/qqmusic/search",
    async (
      req: FastifyRequest<{
        Querystring: { keyword: string; page?: string; pageSize?: string };
      }>,
      reply: FastifyReply,
    ) => {
      const { keyword, page = "1", pageSize = "20" } = req.query;
      if (!keyword) {
        return reply.status(400).send({ code: 400, message: "keyword 参数是必须的" });
      }

      return reply.send(
        await searchQQMusic(keyword, parseInt(page, 10) || 1, parseInt(pageSize, 10) || 20),
      );
    },
  );

  fastify.get(
    "/qqmusic/match",
    async (req: FastifyRequest<{ Querystring: { keyword: string } }>, reply: FastifyReply) => {
      const { keyword } = req.query;
      if (!keyword) {
        return reply.status(400).send({ code: 400, message: "keyword 参数是必须的" });
      }

      const searchResult = await searchQQMusic(keyword, 1, 1);
      if (!searchResult.songs || searchResult.songs.length === 0) {
        return reply.status(404).send({ code: 404, message: "未找到匹配的歌曲" });
      }

      const song = searchResult.songs[0];
      const lyricResult = await getQQMusicLyric(
        parseInt(song.id, 10),
        song.name,
        song.artist,
        song.album,
        Math.floor(song.duration / 1000),
      );

      const { code: _code, ...lyrics } = lyricResult;
      return reply.send({
        code: 200,
        song: {
          id: song.id,
          mid: song.mid,
          name: song.name,
          artist: song.artist,
          album: song.album,
          duration: song.duration,
        },
        ...lyrics,
      });
    },
  );

  serverLog.info("🌐 Register QQMusicAPI successfully");
};
