import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "top.imsyy.splayer",
  appName: "SPlayer",
  webDir: "dist_mobile",
  server: {
    // 允许原生壳访问自建 http API
    androidScheme: "https",
    cleartext: true,
  },
  plugins: {
    // 使用原生网络栈代理 fetch 和 XHR，绕开 WebView 的跨域与混合内容限制
    CapacitorHttp: {
      enabled: true,
    },
    Keyboard: {
      // 键盘弹出时调整 WebView 大小而非推送整个内容
      resize: "body",
      resizeOnFullScreen: true,
    },
  },
};

export default config;
