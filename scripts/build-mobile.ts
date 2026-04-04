import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

type BuildMode = "remote" | "embedded";

const modeArg = process.argv[2];
const mode: BuildMode = modeArg === "embedded" ? "embedded" : "remote";

const normalizePath = (value: string) => value.replace(/\/+$/, "");

const trimServiceSuffix = (value: string) =>
  value.replace(/\/(netease|unblock|qqmusic)$/i, "").replace(/\/+$/, "");

const parseEnvFile = (filePath: string) => {
  if (!existsSync(filePath)) return {};
  return readFileSync(filePath, "utf8")
    .split(/\r?\n/u)
    .reduce<Record<string, string>>((accumulator, line) => {
      const trimmedLine = line.trim();
      if (!trimmedLine || trimmedLine.startsWith("#")) return accumulator;
      const separatorIndex = trimmedLine.indexOf("=");
      if (separatorIndex === -1) return accumulator;
      const key = trimmedLine.slice(0, separatorIndex).trim();
      const value = trimmedLine.slice(separatorIndex + 1).trim();
      accumulator[key] = value;
      return accumulator;
    }, {});
};

const resolveMobileEnv = () => {
  const projectRoot = process.cwd();
  const envFiles = [".env", ".env.mobile", ".env.mobile.local"];
  return envFiles.reduce<Record<string, string>>((accumulator, fileName) => {
    return {
      ...accumulator,
      ...parseEnvFile(path.join(projectRoot, fileName)),
    };
  }, {});
};

const resolveApiRoot = (mobileEnv: Record<string, string>) => {
  const apiRootValue = String(process.env.VITE_API_ROOT || mobileEnv.VITE_API_ROOT || "").trim();
  const legacyApiUrlValue = String(process.env.VITE_API_URL || mobileEnv.VITE_API_URL || "").trim();

  if (apiRootValue) return normalizePath(apiRootValue);
  if (!legacyApiUrlValue) return "/api";

  const normalizedLegacyApiUrl = normalizePath(legacyApiUrlValue);
  if (!/^https?:\/\//iu.test(normalizedLegacyApiUrl)) {
    return normalizedLegacyApiUrl === "/api/netease"
      ? "/api"
      : trimServiceSuffix(normalizedLegacyApiUrl) || "/api";
  }

  try {
    const parsedUrl = new URL(normalizedLegacyApiUrl);
    const rootPath = trimServiceSuffix(parsedUrl.pathname);
    return `${parsedUrl.origin}${rootPath || "/api"}`;
  } catch {
    return "/api";
  }
};

const run = (command: string, args: string[], env: NodeJS.ProcessEnv = process.env) => {
  const result = spawnSync(command, args, {
    stdio: "inherit",
    shell: process.platform === "win32",
    env,
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
};

const mobileEnv = resolveMobileEnv();
const resolvedApiRoot = resolveApiRoot(mobileEnv);
const env = {
  ...process.env,
  VITE_ANDROID_API_MODE: mode,
};

if (mode === "remote") {
  console.info(`\n[android build] remote API root => ${resolvedApiRoot}`);
  if (!process.env.VITE_API_ROOT && !mobileEnv.VITE_API_ROOT && mobileEnv.VITE_API_URL) {
    console.warn("[android build] 检测到旧的 VITE_API_URL 配置，建议迁移到 VITE_API_ROOT。");
  }
}

run("pnpm", ["typecheck:web"], env);
run("pnpm", ["exec", "rimraf", "dist_mobile"], env);
run("pnpm", ["exec", "vite", "build", "-c", "vite.config.mobile.ts", "--mode", "mobile"], env);
