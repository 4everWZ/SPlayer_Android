import type { ApiRuntimeMode, ApiServiceName } from "./types";

const DEFAULT_API_ROOT = "/api";
const LEGACY_NETEASE_PATH = "/api/netease";
const viteEnv = (import.meta as ImportMeta & { env?: Record<string, string> }).env || {};

const normalizePath = (value: string) => value.replace(/\/+$/, "");

const isAbsoluteUrl = (value: string) => /^https?:\/\//i.test(value);

const trimServiceSuffix = (value: string) =>
  value.replace(/\/(netease|unblock|qqmusic)$/i, "").replace(/\/+$/, "");

export const resolveApiRoot = (
  apiRootValue = String(viteEnv["VITE_API_ROOT"] || "").trim(),
  legacyApiUrlValue = String(viteEnv["VITE_API_URL"] || "").trim(),
) => {
  if (apiRootValue) {
    return normalizePath(apiRootValue);
  }

  if (!legacyApiUrlValue) {
    return DEFAULT_API_ROOT;
  }

  const normalizedLegacyApiUrl = normalizePath(legacyApiUrlValue);

  if (!isAbsoluteUrl(normalizedLegacyApiUrl)) {
    if (normalizedLegacyApiUrl === LEGACY_NETEASE_PATH) {
      return DEFAULT_API_ROOT;
    }
    return trimServiceSuffix(normalizedLegacyApiUrl) || DEFAULT_API_ROOT;
  }

  try {
    const parsedUrl = new URL(normalizedLegacyApiUrl);
    const rootPath = trimServiceSuffix(parsedUrl.pathname);
    return `${parsedUrl.origin}${rootPath || DEFAULT_API_ROOT}`;
  } catch {
    return DEFAULT_API_ROOT;
  }
};

const buildServiceUrl = (root: string, service: ApiServiceName) => {
  const normalizedRoot = normalizePath(root || DEFAULT_API_ROOT);
  return `${normalizedRoot}/${service}`;
};

export class ApiEndpointRegistry {
  public readonly mode: ApiRuntimeMode;
  public readonly root: string;

  constructor() {
    this.mode =
      String(viteEnv["VITE_ANDROID_API_MODE"] || "remote").trim() === "embedded"
        ? "embedded"
        : "remote";
    this.root = resolveApiRoot();
  }

  getServiceUrl(service: ApiServiceName) {
    return buildServiceUrl(this.root, service);
  }

  get netease() {
    return this.getServiceUrl("netease");
  }

  get unblock() {
    return this.getServiceUrl("unblock");
  }

  get qqmusic() {
    return this.getServiceUrl("qqmusic");
  }
}

export const apiEndpointRegistry = new ApiEndpointRegistry();
