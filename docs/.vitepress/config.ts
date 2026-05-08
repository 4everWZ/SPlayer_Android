import { defineConfig } from "vitepress";

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "SPlayer",
  description: "一个简约的音乐播放器",
  lang: "zh-CN",
  ignoreDeadLinks: true,
  head: [
    ["link", { rel: "icon", href: "/favicon.png" }],
    ["meta", { name: "author", content: "imsyy" }],
    ["meta", { name: "keywords", content: "SPlayer,音乐播放器,网易云音乐,Electron,Vue3" }],
  ],
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    logo: "/favicon.png",
    siteTitle: "SPlayer",
    nav: [
      { text: "首页", link: "/" },
      { text: "下载", link: "/download" },
      { text: "使用指南", link: "/guide" },
      { text: "API", link: "/api" },
      { text: "GitHub", link: "https://github.com/imsyy/SPlayer" },
    ],

    sidebar: [
      {
        text: "指南",
        items: [
          { text: "下载", link: "/download" },
          { text: "使用指南", link: "/guide" },
          { text: "流媒体服务", link: "/streaming" },
          { text: "服务器 API 部署", link: "/deployment/server-api" },
        ],
      },
      {
        text: "API",
        items: [
          { text: "API 接口文档", link: "/api" },
          { text: "WebSocket API", link: "/socket" },
        ],
      },
      {
        text: "开发指南",
        items: [
          { text: "原生插件", link: "/native" },
          { text: "贡献指南", link: "/contributing" },
        ],
      },
      {
        text: "Android Native V2",
        items: [
          { text: "总览", link: "/specs/00_android_native_v2" },
          { text: "API 规约", link: "/specs/dev_android_api" },
          { text: "播放器规约", link: "/specs/dev_android_player" },
          { text: "UI 规约", link: "/specs/dev_android_ui" },
          { text: "实现矩阵", link: "/specs/matrix_android_native_v2" },
          { text: "集成用例", link: "/specs/integration_android_native_v2" },
          { text: "当前状态", link: "/specs/status_android_native_v2" },
          { text: "架构设计", link: "/design/android_native_architecture" },
          { text: "Tradeoffs", link: "/tradeoffs" },
        ],
      },
      {
        text: "故障排查",
        items: [
          { text: "调试模式和错误排查", link: "/troubleshooting/debug" },
          { text: "macOS 常见问题", link: "/troubleshooting/macos" },
          { text: "Mac 应用显示已损坏", link: "/troubleshooting/macos-damaged" },
          { text: "macOS ARM 设备 API 启动失败", link: "/troubleshooting/macos-arm-api" },
          { text: "Windows 7 系统兼容性问题", link: "/troubleshooting/windows7" },
          { text: "Ubuntu 系统沙箱启动失败", link: "/troubleshooting/ubuntu-sandbox" },
        ],
      },
    ],

    outline: {
      level: [2, 3],
      label: "文章目录",
    },

    socialLinks: [{ icon: "github", link: "https://github.com/imsyy/SPlayer" }],

    footer: {
      message: "基于 AGPL-3.0 许可发布",
      copyright: "Copyright © 2025-present imsyy",
    },

    editLink: {
      pattern: "https://github.com/imsyy/SPlayer/edit/dev/docs/:path",
      text: "查看或编辑此页",
    },

    lastUpdated: {
      text: "最后更新于",
      formatOptions: {
        dateStyle: "short",
        timeStyle: "medium",
      },
    },
  },
});
