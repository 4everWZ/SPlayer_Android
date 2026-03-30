import axios from "axios";
import { randomBytes } from "crypto";
import type { SongMatchInfo, SongUrlResult } from "../types";
import { isSongMatch } from "./match";
import { serverLog } from "../logger";

const search = async (match: SongMatchInfo): Promise<string | null> => {
  try {
    const searchUrl = `https://www.gequbao.com/s/${encodeURIComponent(match.keyword)}`;
    const { data } = await axios.get(searchUrl);

    const regex = /<a href="\/music\/(\d+)" target="_blank" class="music-link d-block">\s*([^<]*)/g;
    let hasResult = false;
    for (const m of data.matchAll(regex)) {
      hasResult = true;
      const songName = m[2]?.trim();
      if (songName && isSongMatch(songName, undefined, match)) {
        return m[1];
      }
    }
    if (!hasResult) return null;
    serverLog.warn(`⚠️ Gequbao 搜索结果均不匹配原曲: "${match.songName}"`);
    return null;
  } catch (error) {
    serverLog.error("❌ Get GequbaoSongId Error:", error);
    return null;
  }
};

const getPlayId = async (id: string): Promise<string | null> => {
  try {
    const url = `https://www.gequbao.com/music/${id}`;
    const { data } = await axios.get(url);
    const match = data.match(/"play_id":"(.*?)"/);
    if (match && match[1]) {
      return match[1];
    }
    return null;
  } catch (error) {
    serverLog.error("❌ Get GequbaoPlayId Error:", error);
    return null;
  }
};

const getGequbaoSongUrl = async (match: SongMatchInfo): Promise<SongUrlResult> => {
  try {
    if (!match.keyword) return { code: 404, url: null };
    const id = await search(match);
    if (!id) return { code: 404, url: null };

    const playId = await getPlayId(id);
    if (!playId) return { code: 404, url: null };

    const url = "https://www.gequbao.com/api/play-url";
    const headers = {
      accept: "application/json, text/javascript, */*; q=0.01",
      "accept-language": "zh-CN,zh;q=0.9",
      "cache-control": "no-cache",
      "content-type": "application/x-www-form-urlencoded; charset=UTF-8",
      pragma: "no-cache",
      priority: "u=1, i",
      "sec-ch-ua": '"Chromium";v="142", "Google Chrome";v="142", "Not_A Brand";v="99"',
      "sec-ch-ua-mobile": "?0",
      "sec-ch-ua-platform": '"Windows"',
      "sec-fetch-dest": "empty",
      "sec-fetch-mode": "cors",
      "sec-fetch-site": "same-origin",
      "x-requested-with": "XMLHttpRequest",
      cookie: `server_name_session=${randomBytes(16).toString("hex")}`,
      Referer: `https://www.gequbao.com/music/${id}`,
    };

    const body = `id=${encodeURIComponent(playId)}`;
    const { data } = await axios.post(url, body, { headers });

    if (data.code === 1 && data.data && data.data.url) {
      serverLog.log("🔗 GequbaoSong URL:", data.data.url);
      return { code: 200, url: data.data.url };
    }

    return { code: 404, url: null };
  } catch (error) {
    serverLog.error("❌ Get GequbaoSong URL Error:", error);
    return { code: 404, url: null };
  }
};

export default getGequbaoSongUrl;
