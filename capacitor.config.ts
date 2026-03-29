import type { CapacitorConfig } from "@capacitor/cli";

const config: CapacitorConfig = {
  appId: "top.imsyy.splayer",
  appName: "SPlayer",
  webDir: "dist_mobile",
  server: {
    androidScheme: "https",
  },
  plugins: {
    Keyboard: {
      // 键盘弹出时调整 WebView 大小而非推送整个内容
      resize: "body",
      resizeOnFullScreen: true,
    },
  },
};

export default config;
