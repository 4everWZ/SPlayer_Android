import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosError,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";
import { useSettingStore, useStatusStore } from "@/stores";
import { getCookie } from "./cookie";
import { isLogin } from "./auth";
import axiosRetry from "axios-retry";
import { isCapacitor } from "./platform";
import { apiEndpointRegistry, apiRuntime } from "@/api/runtime";
import type { ApiServiceName } from "@/api/runtime";

type RequestMeta = {
  silent?: boolean;
  userAction?: boolean;
  dedupeKey?: string;
  errorMessage?: string;
  service?: ApiServiceName;
};

export type AppRequestConfig = AxiosRequestConfig & {
  meta?: RequestMeta;
};

type AppRequestInternalConfig = InternalAxiosRequestConfig & {
  meta?: RequestMeta;
};

const NETWORK_ERROR_COOLDOWN_MS = 6000;
const networkErrorCache = new Map<string, number>();

// 基础配置
const server: AxiosInstance = axios.create({
  // 原生壳使用手动写入的 Cookie，避免因自建 API 的 CORS 头导致请求被拦截
  withCredentials: !isCapacitor,
  // 超时时间
  timeout: 15000,
});

// 请求重试
axiosRetry(server, {
  // 重试次数
  retries: 3,
});

const resolveRequestMeta = (config?: Partial<AppRequestConfig>): Required<RequestMeta> => {
  const method = String(config?.method || "get").toLowerCase();
  const userAction = config?.meta?.userAction ?? !["get", "head", "options"].includes(method);
  const service = config?.meta?.service ?? "netease";
  return {
    silent: config?.meta?.silent ?? !userAction,
    userAction,
    dedupeKey:
      config?.meta?.dedupeKey ??
      `${method}:${config?.url || apiEndpointRegistry.getServiceUrl(service)}`,
    errorMessage: config?.meta?.errorMessage ?? "网络请求超时，请检查网络连接",
    service,
  };
};

const isNetworkError = (error: AxiosError) => {
  const message = error.message?.toLowerCase?.() || "";
  return (
    error.code === "ECONNABORTED" ||
    message.includes("timeout") ||
    message.includes("network error") ||
    message.includes("failed to fetch")
  );
};

const notifyNetworkError = (message: string, dedupeKey: string) => {
  const now = Date.now();
  const lastShownAt = networkErrorCache.get(dedupeKey) || 0;
  if (now - lastShownAt < NETWORK_ERROR_COOLDOWN_MS) return;
  networkErrorCache.set(dedupeKey, now);
  window.$message?.warning(message);
};

// 请求拦截器
server.interceptors.request.use(
  (request: AppRequestInternalConfig) => {
    // pinia
    const settingStore = useSettingStore();
    request.meta = resolveRequestMeta(request);
    if (!request.baseURL) {
      request.baseURL = apiEndpointRegistry.getServiceUrl(request.meta.service || "netease");
    }
    if (!request.params) request.params = {};
    // Cookie
    if (!request.params.noCookie && (isLogin() || getCookie("MUSIC_U") !== null)) {
      const cookie = `MUSIC_U=${getCookie("MUSIC_U")};os=pc;`;
      request.params.cookie = cookie;
    }
    // 自定义 realIP
    if (settingStore.useRealIP) {
      if (settingStore.realIP) {
        request.params.realIP = settingStore.realIP;
      } else {
        request.params.randomCNIP = true;
      }
    }
    // proxy
    if (settingStore.proxyProtocol !== "off") {
      const protocol = settingStore.proxyProtocol.toLowerCase();
      const server = settingStore.proxyServe;
      const port = settingStore.proxyPort;
      const proxy = `${protocol}://${server}:${port}`;
      if (proxy) request.params.proxy = proxy;
    }
    // 发送请求
    return request;
  },
  (error: AxiosError) => {
    console.error("请求发送失败：", error);
    return Promise.reject(error);
  },
);

// 响应拦截器
server.interceptors.response.use(
  (response: AxiosResponse) => {
    useStatusStore().clearNetworkFailure();
    return response;
  },
  (error: AxiosError) => {
    const statusStore = useStatusStore();
    const request = error.config as AppRequestConfig | undefined;
    const meta = resolveRequestMeta(request);

    // 超时/网络错误
    if (isNetworkError(error)) {
      statusStore.markNetworkFailure();
      if (!meta.silent) {
        notifyNetworkError(meta.errorMessage, meta.dedupeKey);
      }
      // 返回 null 而非 reject，业务代码需要检查返回值
      return Promise.resolve({ data: null });
    }

    const { response } = error;
    // 状态码处理（仅记录日志，不触发弹窗）
    switch (response?.status) {
      case 400:
        console.warn("客户端错误：", response.status, response.statusText);
        break;
      case 401:
        console.warn("未授权：", response.status, response.statusText);
        break;
      case 403:
        console.warn("禁止访问：", response.status, response.statusText);
        break;
      case 404:
        console.warn("未找到资源：", response.status, response.statusText);
        break;
      case 500:
        console.warn("服务器错误：", response.status, response.statusText);
        break;
      default:
        console.warn("未处理的错误：", error.message);
    }
    // 返回错误
    return Promise.reject(error);
  },
);

// 请求
const request = async <T = any>(config: AppRequestConfig): Promise<T> => {
  const embeddedResult = await apiRuntime.request<T>(config);
  if (embeddedResult.handled) {
    return embeddedResult.data;
  }
  // 返回请求数据
  const { data } = await server.request(config);
  return data as T;
};

export default request;
