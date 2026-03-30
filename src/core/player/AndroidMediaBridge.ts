import { registerPlugin, type PluginListenerHandle } from "@capacitor/core";
import type { SystemMediaEvent } from "@emi";
import { currentPlatform, isCapacitor } from "@/utils/platform";

type PermissionState = "prompt" | "prompt-with-rationale" | "granted" | "denied";

export type AndroidMediaMetadataPayload = {
  title: string;
  artist: string;
  album: string;
  coverUrl?: string;
  coverBase64?: string;
  duration?: number;
  ncmId?: number;
};

interface AndroidMediaBridgePlugin {
  updateMetadata(options: AndroidMediaMetadataPayload): Promise<void>;
  updatePlayState(options: { status: string }): Promise<void>;
  updatePlaybackRate(options: { rate: number }): Promise<void>;
  updateVolume(options: { volume: number }): Promise<void>;
  updateTimeline(options: {
    currentTime: number;
    totalTime: number;
    seeked?: boolean;
  }): Promise<void>;
  updatePlayMode(options: { isShuffling: boolean; repeatMode: string }): Promise<void>;
  addListener(
    eventName: "media-event",
    listenerFunc: (event: SystemMediaEvent) => void,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
  checkPermissions(): Promise<{ notifications?: PermissionState }>;
  requestPermissions(): Promise<{ notifications?: PermissionState }>;
}

const AndroidMediaBridgePlugin = registerPlugin<AndroidMediaBridgePlugin>("AndroidMediaBridge");

const isSupported = isCapacitor && currentPlatform === "android";

const invokeBridge = async (task: () => Promise<void>) => {
  if (!isSupported) return;
  try {
    await task();
  } catch (error) {
    console.error("[AndroidMediaBridge] 调用失败", error);
  }
};

export const androidMediaBridge = {
  isSupported,
  async ensureNotificationPermission() {
    if (!isSupported) return false;
    try {
      const permissions = await AndroidMediaBridgePlugin.checkPermissions();
      if (permissions.notifications === "granted") return true;
      const requested = await AndroidMediaBridgePlugin.requestPermissions();
      return requested.notifications === "granted";
    } catch (error) {
      console.error("[AndroidMediaBridge] 请求通知权限失败", error);
      return false;
    }
  },
  updateMetadata(payload: AndroidMediaMetadataPayload) {
    return invokeBridge(() => AndroidMediaBridgePlugin.updateMetadata(payload));
  },
  updatePlayState(status: string) {
    return invokeBridge(() => AndroidMediaBridgePlugin.updatePlayState({ status }));
  },
  updatePlaybackRate(rate: number) {
    return invokeBridge(() => AndroidMediaBridgePlugin.updatePlaybackRate({ rate }));
  },
  updateVolume(volume: number) {
    return invokeBridge(() => AndroidMediaBridgePlugin.updateVolume({ volume }));
  },
  updateTimeline(currentTime: number, totalTime: number, seeked?: boolean) {
    return invokeBridge(() =>
      AndroidMediaBridgePlugin.updateTimeline({ currentTime, totalTime, seeked }),
    );
  },
  updatePlayMode(isShuffling: boolean, repeatMode: string) {
    return invokeBridge(() => AndroidMediaBridgePlugin.updatePlayMode({ isShuffling, repeatMode }));
  },
  async addMediaEventListener(listener: (event: SystemMediaEvent) => void) {
    if (!isSupported) return null;
    try {
      return await AndroidMediaBridgePlugin.addListener("media-event", listener);
    } catch (error) {
      console.error("[AndroidMediaBridge] 监听注册失败", error);
      return null;
    }
  },
  removeAllListeners() {
    return invokeBridge(() => AndroidMediaBridgePlugin.removeAllListeners());
  },
};
