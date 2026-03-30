<template>
  <div class="full-player-mobile" ref="mobileStart">
    <!-- 顶部功能栏 -->
    <div class="top-bar">
      <!-- 收起按钮 -->
      <div class="btn" @click.stop="statusStore.showFullPlayer = false">
        <SvgIcon name="Down" :size="26" />
      </div>
      <div class="top-title">
        <div class="title text-hidden">{{ songTitle }}</div>
        <div class="subtitle text-hidden">{{ artistName }}</div>
      </div>
      <div class="top-bar-placeholder"></div>
    </div>

    <!-- 主内容 -->
    <div
      :class="['mobile-content', { swiping: isSwiping }]"
      :style="{ transform: contentTransform }"
      @click.stop
    >
      <!-- 歌曲信息页 -->
      <div class="page info-page">
        <!-- 封面 -->
        <div :class="['cover-section', { clickable: hasLyric }]" @click.stop="openLyricPage">
          <PlayerCover :no-lyric="true" />
          <div v-if="hasLyric" class="cover-tip">点击唱片查看歌词</div>
        </div>

        <!-- 歌曲信息区域 -->
        <div class="info-group">
          <!-- 歌曲信息与操作 -->
          <div class="song-info-bar">
            <div class="info-section">
              <PlayerData :center="false" :light="false" class="mobile-data" />
            </div>
            <div class="info-actions">
              <!-- 喜欢 -->
              <div
                v-if="musicStore.playSong.type !== 'radio'"
                class="action-btn"
                @click="
                  toLikeSong(musicStore.playSong, !dataStore.isLikeSong(musicStore.playSong.id))
                "
              >
                <SvgIcon
                  :name="
                    dataStore.isLikeSong(musicStore.playSong.id) ? 'Favorite' : 'FavoriteBorder'
                  "
                  :size="26"
                  :class="{ liked: dataStore.isLikeSong(musicStore.playSong.id) }"
                />
              </div>
              <!-- 添加到歌单 -->
              <div
                class="action-btn"
                @click.stop="openPlaylistAdd([musicStore.playSong], !!musicStore.playSong.path)"
              >
                <SvgIcon name="AddList" :size="26" />
              </div>
            </div>
          </div>

          <!-- 进度条 -->
          <div class="progress-section">
            <span class="time" @click="toggleTimeFormat">{{ timeDisplay[0] }}</span>
            <PlayerSlider class="player" :show-tooltip="false" />
            <span class="time" @click="toggleTimeFormat">{{ timeDisplay[1] }}</span>
          </div>

          <!-- 主控制按钮 -->
          <div class="control-section">
            <!-- 播放模式 -->
            <template v-if="musicStore.playSong.type !== 'radio' && !statusStore.personalFmMode">
              <div class="mode-btn" @click.stop="player.cyclePlayMode()">
                <SvgIcon :name="statusStore.playerModeIcon" :size="24" />
              </div>
            </template>
            <div v-else class="placeholder"></div>

            <!-- 上一曲 -->
            <div class="ctrl-btn" @click.stop="player.nextOrPrev('prev')">
              <SvgIcon name="SkipPrev" :size="36" />
            </div>

            <!-- 播放/暂停 -->
            <n-button
              :loading="statusStore.playLoading"
              class="play-btn"
              type="primary"
              strong
              secondary
              circle
              @click.stop="player.playOrPause()"
            >
              <template #icon>
                <Transition name="fade" mode="out-in">
                  <SvgIcon
                    :key="statusStore.playStatus ? 'Pause' : 'Play'"
                    :name="statusStore.playStatus ? 'Pause' : 'Play'"
                    :size="40"
                  />
                </Transition>
              </template>
            </n-button>

            <!-- 下一曲 -->
            <div class="ctrl-btn" @click.stop="player.nextOrPrev('next')">
              <SvgIcon name="SkipNext" :size="36" />
            </div>

            <!-- 播放队列 -->
            <template v-if="!statusStore.personalFmMode">
              <div class="mode-btn queue-btn" @click.stop="statusStore.playListShow = true">
                <SvgIcon name="PlayList" :size="24" />
              </div>
            </template>
            <div v-else class="placeholder"></div>
          </div>
        </div>
      </div>

      <!-- 歌词页 -->
      <div class="page lyric-page">
        <div :class="['lyric-header', { clickable: hasLyric }]" @click.stop="openInfoPage">
          <s-image :src="musicStore.getSongCover('s')" class="lyric-cover" />
          <div class="lyric-info">
            <div class="name text-hidden">
              {{
                settingStore.hideBracketedContent
                  ? removeBrackets(musicStore.playSong.name)
                  : musicStore.playSong.name
              }}
            </div>
            <div class="artist text-hidden">{{ artistName }}</div>
          </div>
          <!-- 喜欢按钮 -->
          <div
            v-if="musicStore.playSong.type !== 'radio'"
            class="action-btn"
            @click.stop="
              toLikeSong(musicStore.playSong, !dataStore.isLikeSong(musicStore.playSong.id))
            "
          >
            <SvgIcon
              :name="dataStore.isLikeSong(musicStore.playSong.id) ? 'Favorite' : 'FavoriteBorder'"
              :size="24"
              :class="{ liked: dataStore.isLikeSong(musicStore.playSong.id) }"
            />
          </div>
        </div>
        <div class="lyric-main">
          <PlayerLyric />
        </div>
      </div>
    </div>

    <!-- 页面指示器 -->
    <div class="pagination" v-if="hasLyric">
      <div
        v-for="i in 2"
        :key="i"
        :class="['dot', { active: pageIndex === i - 1 }]"
        @click="pageIndex = i - 1"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useSwipe } from "@vueuse/core";
import { useMusicStore, useStatusStore, useDataStore, useSettingStore } from "@/stores";
import { usePlayerController } from "@/core/player/PlayerController";
import { useTimeFormat } from "@/composables/useTimeFormat";
import { toLikeSong } from "@/utils/auth";
import { openPlaylistAdd } from "@/utils/modal";
import { removeBrackets } from "@/utils/format";

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();
const dataStore = useDataStore();
const player = usePlayerController();
const { timeDisplay, toggleTimeFormat } = useTimeFormat();

const mobileStart = ref<HTMLElement | null>(null);
const pageIndex = ref(0);

const hasLyric = computed(() => {
  return musicStore.isHasLrc && musicStore.playSong.type !== "radio";
});

const songTitle = computed(() => {
  return settingStore.hideBracketedContent
    ? removeBrackets(musicStore.playSong.name)
    : musicStore.playSong.name || "未知曲目";
});

const artistName = computed(() => {
  if (musicStore.playSong.type === "radio") {
    return musicStore.playSong.dj?.creator || "播客电台";
  }
  const artists = musicStore.playSong.artists;
  if (Array.isArray(artists)) {
    return artists
      .map((ar) => (settingStore.hideBracketedContent ? removeBrackets(ar.name) : ar.name))
      .join(" / ");
  }
  return settingStore.hideBracketedContent
    ? removeBrackets(artists as string)
    : (artists as string) || "未知艺术家";
});

const openLyricPage = () => {
  if (!hasLyric.value) return;
  pageIndex.value = 1;
};

const openInfoPage = () => {
  pageIndex.value = 0;
};

// 没有歌词强制回到第一页
watch(hasLyric, (val) => {
  if (!val) pageIndex.value = 0;
});

watch(
  () => musicStore.playSong.id,
  () => {
    pageIndex.value = 0;
  },
);

// 滑动偏移量
const swipeOffset = ref(0);

const { direction, isSwiping, lengthX } = useSwipe(mobileStart, {
  threshold: 10,
  onSwipe: () => {
    if (!hasLyric.value) return;
    // 为正表示向左滑，为负表示向右滑
    swipeOffset.value = lengthX.value;
  },
  onSwipeEnd: () => {
    if (!hasLyric.value) {
      swipeOffset.value = 0;
      return;
    }
    // 超过阈值则切换页面
    if (direction.value === "left" && lengthX.value > 100) {
      pageIndex.value = 1;
    } else if (direction.value === "right" && lengthX.value < -100) {
      pageIndex.value = 0;
    }
    swipeOffset.value = 0;
  },
});

// 计算实时的变换位置
const contentTransform = computed(() => {
  const baseOffset = pageIndex.value * 50; // 百分比
  if (!isSwiping.value || !hasLyric.value) {
    return `translateX(-${baseOffset}%)`;
  }
  let pixelOffset = lengthX.value;
  // 限制滑动范围
  if (pageIndex.value === 0 && pixelOffset < 0) {
    pixelOffset = pixelOffset * 0.3;
  }
  if (pageIndex.value === 1 && pixelOffset > 0) {
    pixelOffset = pixelOffset * 0.3;
  }
  return `translateX(calc(-${baseOffset}% - ${pixelOffset}px))`;
});
</script>

<style lang="scss" scoped>
.full-player-mobile {
  --mobile-cover-size: min(76vw, 40vh);
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  .top-bar {
    position: absolute;
    width: 100%;
    height: calc(72px + var(--safe-area-inset-top));
    flex-shrink: 0;
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 12px;
    padding: var(--safe-area-inset-top) 20px 0;
    z-index: 10;
    .btn {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: background-color 0.2s;
      flex-shrink: 0;
      &:active {
        background-color: rgba(255, 255, 255, 0.1);
      }
      .n-icon {
        color: rgb(var(--main-cover-color));
        opacity: 0.8;
      }
    }
    .top-title {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding-bottom: 4px;
      .title {
        max-width: 100%;
        font-size: 17px;
        font-weight: 700;
        line-height: 1.3;
      }
      .subtitle {
        max-width: 100%;
        margin-top: 2px;
        font-size: 12px;
        opacity: 0.62;
      }
    }
    .top-bar-placeholder {
      width: 40px;
      height: 40px;
      flex-shrink: 0;
    }
  }
  .mobile-content {
    flex: 1;
    display: flex;
    width: 200%;
    height: 100%;
    transition: transform 0.3s cubic-bezier(0.25, 1, 0.5, 1);
    &.swiping {
      transition: none;
    }
    .page {
      width: 50%;
      height: 100%;
      flex-shrink: 0;
      position: relative;
    }
    .info-page {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 0 20px calc(24px + var(--safe-area-inset-bottom)) 20px;
      overflow: hidden;
      .cover-section {
        flex: 1 1 auto;
        min-height: 0;
        width: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-direction: column;
        margin-top: calc(76px + var(--safe-area-inset-top));
        margin-bottom: 12px;
        &.clickable {
          cursor: pointer;
        }
        .cover-tip {
          margin-top: 14px;
          font-size: 13px;
          opacity: 0.58;
          color: rgb(var(--main-cover-color));
        }
        :deep(.player-cover) {
          width: var(--mobile-cover-size);
          max-width: none;
          &.record {
            width: var(--mobile-cover-size);
            margin-bottom: 0;
            .cover-img {
              width: var(--mobile-cover-size);
              height: var(--mobile-cover-size);
              min-width: 0;
            }
          }
        }
      }
      .info-group {
        width: 100%;
        display: flex;
        flex-direction: column;
        flex-shrink: 0;
        .song-info-bar {
          width: 100%;
          display: flex;
          justify-content: space-between;
          margin-bottom: 18px;
          .info-section {
            flex: 1;
            min-width: 0;
            margin-right: 12px;
            :deep(.mobile-data) {
              width: 100%;
              max-width: 100%;
              margin-top: 0;
              padding: 0;
              .name {
                margin-left: 0;
                .name-text {
                  font-size: 28px;
                  line-height: 1.18;
                }
              }
              .artists,
              .album,
              .dj {
                font-size: 15px;
              }
              .alia {
                margin: 4px 0;
                font-size: 14px;
              }
            }
          }
          .info-actions {
            display: flex;
            padding-top: 10px;
            gap: 16px;
            flex-shrink: 0;
            .action-btn {
              display: flex;
              align-items: center;
              justify-content: center;
              width: 40px;
              height: 40px;
              border-radius: 50%;
              cursor: pointer;
              transition: background-color 0.2s;
              &:active {
                background-color: rgba(255, 255, 255, 0.1);
              }
              .n-icon {
                color: rgb(var(--main-cover-color));
                opacity: 0.6;
                transition:
                  opacity 0.2s,
                  transform 0.2s;
                &.liked {
                  fill: rgb(var(--main-cover-color));
                  opacity: 1;
                }
              }
            }
          }
        }
        .progress-section {
          display: flex;
          align-items: center;
          margin: 0 0 20px;
          .time {
            font-size: 12px;
            opacity: 0.6;
            width: 40px;
            text-align: center;
            color: rgb(var(--main-cover-color));
            font-variant-numeric: tabular-nums;
          }
          .n-slider {
            margin: 0 12px;
          }
        }
        .control-section {
          width: 100%;
          max-width: 400px;
          margin: 0 auto;
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0 10px;
          .placeholder {
            width: 24px;
          }
          .mode-btn {
            opacity: 0.8;
            cursor: pointer;
            width: 40px;
            height: 40px;
            display: flex;
            align-items: center;
            justify-content: center;
            .n-icon {
              color: rgb(var(--main-cover-color));
            }
          }
          .ctrl-btn {
            cursor: pointer;
            width: 50px;
            height: 50px;
            display: flex;
            align-items: center;
            justify-content: center;
            .n-icon {
              color: rgb(var(--main-cover-color));
            }
          }
          .play-btn {
            width: 60px;
            height: 60px;
            font-size: 26px;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            transition: transform 0.2s;
            background-color: rgba(var(--main-cover-color), 0.2);
            color: rgb(var(--main-cover-color));
            &.n-button--primary-type {
              --n-color: rgba(var(--main-cover-color), 0.14);
              --n-color-hover: rgba(var(--main-cover-color), 0.2);
              --n-color-focus: rgba(var(--main-cover-color), 0.2);
              --n-color-pressed: rgba(var(--main-cover-color), 0.12);
            }
            &:active {
              transform: scale(0.95);
            }
          }
        }
      }
    }
    .lyric-page {
      padding: 0 24px;
      padding-top: calc(60px + var(--safe-area-inset-top));
      display: flex;
      flex-direction: column;
      .lyric-header {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-bottom: 20px;
        flex-shrink: 0;
        padding: 10px 20px 0;
        &.clickable {
          cursor: pointer;
        }
        .lyric-cover {
          width: 50px;
          height: 50px;
          flex-shrink: 0;
          :deep(img) {
            border-radius: 6px;
            width: 100%;
            height: 100%;
          }
          border-radius: 6px;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }
        .lyric-info {
          flex: 1;
          min-width: 0;
          display: flex;
          flex-direction: column;
          justify-content: center;
          .name {
            font-size: 18px;
            font-weight: bold;
            margin-bottom: 2px;
          }
          .artist {
            font-size: 13px;
            opacity: 0.6;
          }
        }
        .action-btn {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 40px;
          height: 40px;
          border-radius: 50%;
          cursor: pointer;
          transition: background-color 0.2s;
          margin-left: 4px;
          &:active {
            background-color: rgba(255, 255, 255, 0.1);
          }
          .n-icon {
            color: rgb(var(--main-cover-color));
            opacity: 0.6;
            transition: all 0.2s;
            &.liked {
              fill: rgb(var(--main-cover-color));
              opacity: 1;
            }
          }
        }
      }
      .lyric-main {
        flex: 1;
        min-height: 0;
        position: relative;
      }
    }
  }
  .pagination {
    position: absolute;
    bottom: calc(18px + var(--safe-area-inset-bottom));
    left: 0;
    width: 100%;
    display: flex;
    justify-content: center;
    gap: 8px;
    pointer-events: none;
    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: rgba(255, 255, 255, 0.2);
      transition: all 0.3s;
      &.active {
        background-color: rgb(var(--main-cover-color));
        width: 16px;
        border-radius: 4px;
        opacity: 0.8;
      }
    }
  }
  @media (max-height: 860px) {
    --mobile-cover-size: min(72vw, 36vh);
    .mobile-content {
      .info-page {
        .cover-section {
          margin-bottom: 8px;
        }
        .info-group {
          .song-info-bar {
            margin-bottom: 14px;
          }
          .progress-section {
            margin-bottom: 16px;
          }
        }
      }
    }
  }
  @media (max-height: 760px) {
    --mobile-cover-size: min(66vw, 32vh);
    .top-bar {
      height: calc(64px + var(--safe-area-inset-top));
    }
    .mobile-content {
      .info-page {
        padding-bottom: calc(18px + var(--safe-area-inset-bottom));
        .cover-section {
          margin-top: calc(68px + var(--safe-area-inset-top));
          .cover-tip {
            margin-top: 10px;
            font-size: 12px;
          }
        }
        .info-group {
          .song-info-bar {
            margin-bottom: 10px;
            .info-section {
              :deep(.mobile-data) {
                .name {
                  .name-text {
                    font-size: 24px;
                  }
                }
              }
            }
          }
          .progress-section {
            margin-bottom: 12px;
          }
        }
      }
    }
    .pagination {
      bottom: calc(14px + var(--safe-area-inset-bottom));
    }
  }
}
</style>
