import { spawnSync } from "node:child_process";

type BuildMode = "remote" | "embedded";

const modeArg = process.argv[2];
const mode: BuildMode = modeArg === "embedded" ? "embedded" : "remote";

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

const env = {
  ...process.env,
  VITE_ANDROID_API_MODE: mode,
};

run("pnpm", ["typecheck:web"], env);
run("pnpm", ["exec", "rimraf", "dist_mobile"], env);
run("pnpm", ["exec", "vite", "build", "-c", "vite.config.mobile.ts", "--mode", "mobile"], env);
