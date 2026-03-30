<template>
  <n-drawer
    v-model:show="statusStore.playerModePanelOpen"
    placement="bottom"
    class="player-mode-drawer"
    height="auto"
  >
    <n-drawer-content
      title="播放模式"
      :native-scrollbar="false"
      :body-content-style="{ padding: '0 16px calc(var(--safe-area-inset-bottom) + 16px)' }"
    >
      <n-flex vertical size="small" class="mode-list">
        <n-button
          v-for="item in modeOptions"
          :key="item.key"
          :type="statusStore.playerModeKey === item.key ? 'primary' : 'default'"
          :disabled="item.disabled"
          block
          strong
          secondary
          @click="applyMode(item.key)"
        >
          <template #icon>
            <SvgIcon :name="item.icon" />
          </template>
          <div class="mode-copy">
            <span class="label">{{ item.label }}</span>
            <span v-if="item.tip" class="tip">{{ item.tip }}</span>
          </div>
        </n-button>
      </n-flex>
    </n-drawer-content>
  </n-drawer>
</template>

<script setup lang="ts">
import { usePlayerController } from "@/core/player/PlayerController";
import { useMusicStore, useStatusStore } from "@/stores";
import type { PlayerModeKey } from "@/types/shared/play-mode";

const statusStore = useStatusStore();
const musicStore = useMusicStore();
const player = usePlayerController();

const canUseHeartbeat = computed(() => {
  return (
    !statusStore.personalFmMode && musicStore.playSong.type !== "radio" && !musicStore.playSong.path
  );
});

const modeOptions = computed(() => [
  {
    key: "repeat-off" as PlayerModeKey,
    label: "顺序播放",
    tip: "播完当前队列后停止",
    icon: "List",
    disabled: false,
  },
  {
    key: "repeat-list" as PlayerModeKey,
    label: "列表循环",
    tip: "按当前队列顺序循环播放",
    icon: "Repeat",
    disabled: false,
  },
  {
    key: "repeat-one" as PlayerModeKey,
    label: "单曲循环",
    tip: "重复播放当前歌曲",
    icon: "RepeatSong",
    disabled: false,
  },
  {
    key: "shuffle" as PlayerModeKey,
    label: "随机播放",
    tip: "打乱当前播放队列",
    icon: "Shuffle",
    disabled: false,
  },
  {
    key: "heartbeat" as PlayerModeKey,
    label: "心动模式",
    tip: canUseHeartbeat.value ? "按当前歌曲生成推荐队列" : "当前歌曲不支持心动模式",
    icon: "HeartBit",
    disabled: !canUseHeartbeat.value,
  },
]);

const applyMode = async (mode: PlayerModeKey) => {
  statusStore.playerModePanelOpen = false;
  await player.setPlayMode(mode);
};
</script>

<style scoped lang="scss">
.mode-list {
  .n-button {
    justify-content: flex-start;
    min-height: 64px;
  }
}

.mode-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  text-align: left;

  .label {
    font-weight: 600;
  }

  .tip {
    margin-top: 4px;
    font-size: 12px;
    opacity: 0.68;
    white-space: normal;
  }
}
</style>
