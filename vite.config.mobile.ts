import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";
import { resolve } from "path";
import AutoImport from "unplugin-auto-import/vite";
import { NaiveUiResolver } from "unplugin-vue-components/resolvers";
import Components from "unplugin-vue-components/vite";
import viteCompression from "vite-plugin-compression";
import wasm from "vite-plugin-wasm";

const commonResolve = {
  alias: {
    "@": resolve(__dirname, "src/"),
    "@emi": resolve(__dirname, "native/external-media-integration"),
    "@shared": resolve(__dirname, "src/types/shared"),
    "@opencc": resolve(__dirname, "native/ferrous-opencc-wasm/pkg"),
    "@native": resolve(__dirname, "native"),
  },
};

export default defineConfig(() => {
  return {
    root: ".",
    plugins: [
      vue(),
      AutoImport({
        imports: [
          "vue",
          "vue-router",
          "@vueuse/core",
          {
            "naive-ui": ["useDialog", "useMessage", "useNotification", "useLoadingBar"],
          },
        ],
        eslintrc: {
          enabled: true,
          filepath: "./auto-eslint.mjs",
        },
      }),
      Components({
        resolvers: [NaiveUiResolver()],
      }),
      viteCompression(),
      wasm(),
    ],
    resolve: commonResolve,
    css: {
      preprocessorOptions: {
        scss: {
          silenceDeprecations: ["legacy-js-api"],
        },
      },
    },
    build: {
      outDir: "dist_mobile",
      emptyOutDir: true,
      minify: "terser",
      target: "es2022",
      publicDir: resolve(__dirname, "public"),
      rollupOptions: {
        input: {
          index: resolve(__dirname, "index.html"),
        },
        output: {
          manualChunks: {
            stores: ["src/stores/data.ts", "src/stores/index.ts"],
          },
        },
      },
      terserOptions: {
        compress: {
          pure_funcs: ["console.log"],
        },
      },
      sourcemap: false,
    },
  };
});
