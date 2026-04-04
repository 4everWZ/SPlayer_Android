import type { ApiRuntimeRequestConfig, EmbeddedApiRuntimeProvider } from "./types";

export class EmbeddedUnblockRuntime implements EmbeddedApiRuntimeProvider {
  readonly service = "unblock" as const;

  canHandle(_config: ApiRuntimeRequestConfig) {
    return false;
  }

  async request<T>(_config: ApiRuntimeRequestConfig): Promise<T> {
    throw new Error("EmbeddedUnblockRuntime is not implemented.");
  }
}
