import type { PluginListenerHandle } from "@capacitor/core";
import router from "@/router";
import { useStatusStore } from "@/stores";
import { currentPlatform, isCapacitor } from "@/utils/platform";
import { App, type BackButtonListenerEvent } from "@capacitor/app";

const ROOT_BACK_INTERVAL_MS = 2000;

let backButtonListener: PluginListenerHandle | null = null;

const hasHistoryBack = (event: BackButtonListenerEvent) => {
  return !!window.history.state?.back || !!event.canGoBack || window.history.length > 1;
};

const closeSearchFocus = () => {
  const activeElement = document.activeElement as HTMLElement | null;
  activeElement?.blur?.();
};

const handleAndroidBack = async (event: BackButtonListenerEvent) => {
  const statusStore = useStatusStore();

  if (statusStore.closeTopOverlay()) {
    closeSearchFocus();
    return;
  }

  if (hasHistoryBack(event)) {
    await router.back();
    return;
  }

  const now = Date.now();
  if (statusStore.shouldMinimizeOnBack(now, ROOT_BACK_INTERVAL_MS)) {
    statusStore.resetBackPressed();
    await App.minimizeApp();
    return;
  }

  statusStore.markBackPressed(now);
  window.$message?.info("再按一次返回桌面");
};

export const initAndroidBackButton = async () => {
  if (!isCapacitor || currentPlatform !== "android") return;
  await backButtonListener?.remove();
  backButtonListener = await App.addListener("backButton", handleAndroidBack);
};

export const disposeAndroidBackButton = async () => {
  await backButtonListener?.remove();
  backButtonListener = null;
};
