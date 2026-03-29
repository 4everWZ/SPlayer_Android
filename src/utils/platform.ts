import { Capacitor } from "@capacitor/core";

/** 是否为 Capacitor 原生环境 */
export const isCapacitor = Capacitor.isNativePlatform();

/** 当前运行平台 */
export const currentPlatform = Capacitor.getPlatform();

/** 判断是否为原生平台 */
export const isNativePlatform = () => Capacitor.isNativePlatform();

type ShareContentOptions = {
  title?: string;
  text?: string;
  url?: string;
  dialogTitle?: string;
};

/**
 * 降级复制文本
 */
const copyShareFallback = async (content: string): Promise<boolean> => {
  if (!content) return false;
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(content);
      window.$message?.success("链接已复制到剪贴板");
      return true;
    } catch (error) {
      console.error("clipboard.writeText 失败，尝试降级方案", error);
    }
  }
  try {
    const textarea = document.createElement("textarea");
    textarea.value = content;
    textarea.style.position = "fixed";
    textarea.style.top = "-9999px";
    textarea.style.left = "-9999px";
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    const success = document.execCommand("copy");
    document.body.removeChild(textarea);
    if (!success) throw new Error("execCommand 返回 false");
    window.$message?.success("链接已复制到剪贴板");
    return true;
  } catch (error) {
    console.error("复制分享链接失败：", error);
    return false;
  }
};

/**
 * 统一分享能力
 * Capacitor 原生环境使用系统分享面板，Web 降级到 navigator.share，都不支持则复制到剪贴板
 */
export const shareContent = async (options: ShareContentOptions): Promise<boolean> => {
  const fallbackContent = options.url || options.text || "";
  try {
    if (isCapacitor) {
      const { Share } = await import("@capacitor/share");
      await Share.share(options);
      return true;
    }
    // Web Share API 降级
    if (navigator.share) {
      await navigator.share({
        title: options.title,
        text: options.text,
        url: options.url,
      });
      return true;
    }
    if (await copyShareFallback(fallbackContent)) {
      return true;
    }
    window.$message?.warning("当前环境不支持分享");
    return false;
  } catch (error: any) {
    // 用户取消不算错误
    if (error?.name === "AbortError") return false;
    console.error("分享失败：", error);
    if (await copyShareFallback(fallbackContent)) {
      return true;
    }
    return false;
  }
};

/** 统一分享链接 */
export const shareLink = async (
  url: string,
  options: Omit<ShareContentOptions, "url"> = {},
): Promise<boolean> => {
  if (!url) return false;
  return shareContent({
    ...options,
    url,
  });
};

/**
 * 打开外部链接
 * Capacitor 使用系统浏览器打开，Web 使用 window.open
 */
export const openExternalLink = async (
  url: string,
  target: "_blank" | "_self" = "_blank",
): Promise<void> => {
  if (!url) return;
  try {
    if (isCapacitor) {
      const { Browser } = await import("@capacitor/browser");
      await Browser.open({ url });
      return;
    }
  } catch {
    // 降级
  }
  if (target === "_self") {
    window.location.href = url;
    return;
  }
  window.open(url, target, "noopener,noreferrer");
};
