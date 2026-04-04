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
      // 保持系统正常 resize，避免全屏模式下被插件二次裁切
      resize: "native",
      resizeOnFullScreen: false,
    },
  },
};

export default config;
