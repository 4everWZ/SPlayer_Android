export type ApiServiceName = "netease" | "unblock" | "qqmusic";

export type ApiRuntimeMode = "remote" | "embedded";

export type ApiRuntimeRequestConfig = {
  url?: string;
  method?: string;
  params?: Record<string, unknown>;
  data?: unknown;
  baseURL?: string;
  meta?: {
    service?: ApiServiceName;
  };
};

export type ApiRuntimeProviderResult<T = unknown> = { handled: true; data: T } | { handled: false };

export interface EmbeddedApiRuntimeProvider {
  readonly service: ApiServiceName;
  canHandle(config: ApiRuntimeRequestConfig): boolean;
  request<T>(config: ApiRuntimeRequestConfig): Promise<T>;
}
