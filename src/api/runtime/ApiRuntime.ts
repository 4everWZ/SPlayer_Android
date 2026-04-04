import { EmbeddedNeteaseRuntime } from "./EmbeddedNeteaseRuntime";
import { EmbeddedQQMusicRuntime } from "./EmbeddedQQMusicRuntime";
import { EmbeddedUnblockRuntime } from "./EmbeddedUnblockRuntime";
import { apiEndpointRegistry } from "./ApiEndpointRegistry";
import type {
  ApiRuntimeProviderResult,
  ApiRuntimeRequestConfig,
  ApiServiceName,
  EmbeddedApiRuntimeProvider,
} from "./types";

export class ApiRuntime {
  private readonly providers = new Map<ApiServiceName, EmbeddedApiRuntimeProvider[]>();

  constructor() {
    this.register(new EmbeddedNeteaseRuntime());
    this.register(new EmbeddedUnblockRuntime());
    this.register(new EmbeddedQQMusicRuntime());
  }

  get mode() {
    return apiEndpointRegistry.mode;
  }

  register(provider: EmbeddedApiRuntimeProvider) {
    const providerList = this.providers.get(provider.service) || [];
    providerList.push(provider);
    this.providers.set(provider.service, providerList);
  }

  async request<T>(config: ApiRuntimeRequestConfig): Promise<ApiRuntimeProviderResult<T>> {
    if (this.mode !== "embedded") {
      return { handled: false };
    }

    const service = config.meta?.service || "netease";
    const providerList = this.providers.get(service) || [];
    const provider = providerList.find((item) => item.canHandle(config));

    if (!provider) {
      return { handled: false };
    }

    return {
      handled: true,
      data: await provider.request<T>(config),
    };
  }
}

export const apiRuntime = new ApiRuntime();
