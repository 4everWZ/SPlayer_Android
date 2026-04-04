import type { ApiRuntimeRequestConfig, EmbeddedApiRuntimeProvider } from "./types";

export class EmbeddedNeteaseRuntime implements EmbeddedApiRuntimeProvider {
  readonly service = "netease" as const;

  canHandle(_config: ApiRuntimeRequestConfig) {
    return false;
  }

  async request<T>(_config: ApiRuntimeRequestConfig): Promise<T> {
    throw new Error("EmbeddedNeteaseRuntime is not implemented.");
  }
}
