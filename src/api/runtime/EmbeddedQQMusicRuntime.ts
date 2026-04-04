import type { ApiRuntimeRequestConfig, EmbeddedApiRuntimeProvider } from "./types";

export class EmbeddedQQMusicRuntime implements EmbeddedApiRuntimeProvider {
  readonly service = "qqmusic" as const;

  canHandle(_config: ApiRuntimeRequestConfig) {
    return false;
  }

  async request<T>(_config: ApiRuntimeRequestConfig): Promise<T> {
    throw new Error("EmbeddedQQMusicRuntime is not implemented.");
  }
}
