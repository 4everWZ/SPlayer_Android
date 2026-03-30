import type { SongMatchInfo } from "../types";

export const normalizeName = (name: string): string => {
  return name
    .toLowerCase()
    .replace(/[（(][^）)]*[）)]/g, "")
    .trim();
};

export const normalizeArtist = (artist: string): string => {
  return artist
    .toLowerCase()
    .replace(/[&/、，,;；]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
};

export const isSongMatch = (
  resultName: string,
  resultArtist: string | undefined,
  match: SongMatchInfo,
): boolean => {
  const normalizedResult = normalizeName(resultName);
  const normalizedOriginal = normalizeName(match.songName);
  if (!normalizedResult) return false;
  if (normalizedOriginal) {
    if (
      !normalizedResult.includes(normalizedOriginal) &&
      !normalizedOriginal.includes(normalizedResult)
    ) {
      return false;
    }
  }
  if (resultArtist && match.artist) {
    const normalizedResultArtist = normalizeArtist(resultArtist);
    const normalizedOriginalArtist = normalizeArtist(match.artist);
    if (normalizedResultArtist && normalizedOriginalArtist) {
      if (
        !normalizedResultArtist.includes(normalizedOriginalArtist) &&
        !normalizedOriginalArtist.includes(normalizedResultArtist)
      ) {
        return false;
      }
    }
  }
  return true;
};
