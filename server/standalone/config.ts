export const serverPort = Number(process.env["PORT"] || process.env["VITE_SERVER_PORT"] || 25884);

export const serverHost = process.env["HOST"] || "0.0.0.0";

export const defaultAMLLDbServer =
  process.env["AMLL_DB_SERVER"] || "https://amlldb.bikonoo.com/ncm-lyrics/%s.ttml";
