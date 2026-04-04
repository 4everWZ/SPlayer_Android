<template>
  <div class="full-player-mobile">
    <div class="top-bar">
      <div class="btn" @click.stop="statusStore.showFullPlayer = false">
        <SvgIcon name="Down" :size="26" />
      </div>
      <div class="top-title">
        <div class="title text-hidden">{{ songTitle }}</div>
        <div class="subtitle text-hidden">{{ artistName }}</div>
      </div>
      <div class="top-bar-placeholder"></div>
    </div>

    <div class="mobile-body" @click.stop>
      <div class="stage">
        <Transition name="fade" mode="out-in">
          <div v-if="panelMode === 'info'" key="info" class="cover-stage">
            <div :class="['cover-section', { clickable: hasLyric }]" @click.stop="openLyricPage">
              <PlayerCover :no-lyric="true" />
              <div v-if="hasLyric" class="cover-tip">点击唱片查看歌词</div>
            </div>
          </div>

          <div v-else key="lyric" class="lyric-stage">
            <div :class="['lyric-header', { clickable: hasLyric }]" @click.stop="openInfoPage">
              <s-image :src="musicStore.getSongCover('s')" class="lyric-cover" />
              <div class="lyric-info">
                <div class="name text-hidden">{{ songTitle }}</div>
                <div class="artist text-hidden">{{ artistName }}</div>
              </div>
              <div class="lyric-header-tip">返回封面</div>
            </div>
            <div class="lyric-main">
              <PlayerLyric />
            </div>
          </div>
        </Transition>
      </div>

      <div class="meta-stage">
        <div class="song-info-bar">
          <div class="info-section">
            <PlayerData :center="false" :light="false" class="mobile-data" />
          </div>
          <div class="info-actions">
            <div
              v-if="musicStore.playSong.type !== 'radio'"
              class="action-btn"
              @click="
                toLikeSong(musicStore.playSong, !dataStore.isLikeSong(musicStore.playSong.id))
              "
            >
              <SvgIcon
                :name="dataStore.isLikeSong(musicStore.playSong.id) ? 'Favorite' : 'FavoriteBorder'"
                :size="24"
                :class="{ liked: dataStore.isLikeSong(musicStore.playSong.id) }"
              />
            </div>
            <n-badge
              v-if="showCommentEntry"
              :value="commentCountLabel"
              :show="statusStore.songCommentCount > 0"
            >
              <div class="action-btn" @click.stop="statusStore.showPlayerComment = true">
                <SvgIcon name="Message" :size="24" />
              </div>
            </n-badge>
            <div
              class="action-btn"
              @click.stop="openPlaylistAdd([musicStore.playSong], !!musicStore.playSong.path)"
            >
              <SvgIcon name="AddList" :size="24" />
            </div>
          </div>
        </div>

        <div class="progress-section">
          <span class="time" @click="toggleTimeFormat">{{ timeDisplay[0] }}</span>
          <PlayerSlider class="player" :show-tooltip="false" />
          <span class="time" @click="toggleTimeFormat">{{ timeDisplay[1] }}</span>
        </div>

        <div class="control-section">
          <template v-if="musicStore.playSong.type !== 'radio' && !statusStore.personalFmMode">
            <div class="mode-btn" @click.stop="player.cyclePlayMode()">
              <SvgIcon :name="statusStore.playerModeIcon" :size="24" />
            </div>
          </template>
          <div v-else class="placeholder"></div>

          <div class="ctrl-btn" @click.stop="player.nextOrPrev('prev')">
            <SvgIcon name="SkipPrev" :size="34" />
          </div>

          <n-button
            :loading="statusStore.playLoading || statusStore.playRecovering"
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

          <div class="ctrl-btn" @click.stop="player.nextOrPrev('next')">
            <SvgIcon name="SkipNext" :size="34" />
          </div>

          <template v-if="!statusStore.personalFmMode">
            <div class="mode-btn queue-btn" @click.stop="statusStore.playListShow = true">
              <SvgIcon name="PlayList" :size="24" />
            </div>
          </template>
          <div v-else class="placeholder"></div>
        </div>
      </div>
    </div>

    <n-drawer
      v-model:show="statusStore.showPlayerComment"
      :auto-focus="false"
      class="mobile-player-comment full-player-comment"
      placement="bottom"
      :height="commentDrawerHeight"
    >
      <n-drawer-content
        :native-scrollbar="false"
        :body-content-style="commentDrawerBodyStyle"
        closable
      >
        <template #header>
          <div class="comment-header">
            <n-text class="name">歌曲评论</n-text>
            <n-text class="count" depth="3">
              {{
                statusStore.songCommentCount > 0 ? `${statusStore.songCommentCount} 条` : "暂无计数"
              }}
            </n-text>
          </div>
        </template>
        <ListComment v-if="showCommentEntry && commentId > 0" :id="commentId" :type="commentType" />
        <n-empty v-else description="当前歌曲暂无评论" style="margin-top: 56px" />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { getComment } from "@/api/comment";
import ListComment from "@/components/List/ListComment.vue";
import { useTimeFormat } from "@/composables/useTimeFormat";
import { usePlayerController } from "@/core/player/PlayerController";
import { useDataStore, useMusicStore, useSettingStore, useStatusStore } from "@/stores";
import { toLikeSong } from "@/utils/auth";
import { removeBrackets, formatCommentCount } from "@/utils/format";
import { openPlaylistAdd } from "@/utils/modal";

const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();
const dataStore = useDataStore();
const player = usePlayerController();
const { timeDisplay, toggleTimeFormat } = useTimeFormat();

const panelMode = ref<"info" | "lyric">("info");

const hasLyric = computed(() => musicStore.isHasLrc && musicStore.playSong.type !== "radio");

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
      .map((artist) =>
        settingStore.hideBracketedContent ? removeBrackets(artist.name) : artist.name,
      )
      .join(" / ");
  }
  return settingStore.hideBracketedContent
    ? removeBrackets(artists as string)
    : (artists as string) || "未知艺术家";
});

const commentId = computed(() =>
  typeof musicStore.playSong.id === "number" ? musicStore.playSong.id : 0,
);
const commentType = computed<0 | 4>(() => (musicStore.playSong.type === "radio" ? 4 : 0));
const showCommentEntry = computed(() => !musicStore.playSong.path && commentId.value > 0);
const commentCountLabel = computed(() => formatCommentCount(statusStore.songCommentCount));
const commentDrawerHeight = computed(() => "calc(100dvh - var(--safe-area-inset-top) - 20px)");
const commentDrawerBodyStyle = computed(() => ({
  padding: "0 0 calc(var(--safe-area-inset-bottom) + 16px)",
}));

const openLyricPage = () => {
  if (!hasLyric.value) return;
  panelMode.value = "lyric";
};

const openInfoPage = () => {
  panelMode.value = "info";
};

const fetchCommentCount = async () => {
  if (!showCommentEntry.value || !settingStore.fullscreenPlayerElements.commentCount) return;
  try {
    const result = await getComment(commentId.value, commentType.value, 1, 1);
    if (result.data?.totalCount != null) {
      statusStore.songCommentCount = result.data.totalCount;
    }
  } catch {
    statusStore.songCommentCount = 0;
  }
};

watch(
  () => hasLyric.value,
  (value) => {
    if (!value) {
      panelMode.value = "info";
    }
  },
);

watch(
  () => musicStore.playSong.id,
  () => {
    panelMode.value = "info";
    statusStore.showPlayerComment = false;
    statusStore.songCommentCount = 0;
    void fetchCommentCount();
  },
  { immediate: true },
);

watch(
  () => settingStore.fullscreenPlayerElements.commentCount,
  (value) => {
    if (value && statusStore.songCommentCount === 0) {
      void fetchCommentCount();
    }
  },
);
</script>

<style lang="scss" scoped>
.full-player-mobile {
  --mobile-cover-size: min(74vw, 38vh);
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;

  .top-bar {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: calc(72px + var(--safe-area-inset-top));
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

      &:active {
        background-color: rgba(255, 255, 255, 0.08);
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
        font-size: 18px;
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

  .mobile-body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: calc(76px + var(--safe-area-inset-top)) 20px calc(24px + var(--safe-area-inset-bottom));
  }

  .stage {
    flex: 1;
    min-height: 0;
    display: flex;
  }

  .cover-stage,
  .lyric-stage {
    width: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  .cover-section {
    flex: 1;
    min-height: 0;
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-direction: column;

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

  .lyric-stage {
    padding-top: 8px;
  }

  .lyric-header {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 6px 0 12px;
    flex-shrink: 0;

    &.clickable {
      cursor: pointer;
    }

    .lyric-cover {
      width: 48px;
      height: 48px;
      flex-shrink: 0;
      border-radius: 10px;
      overflow: hidden;

      :deep(img) {
        width: 100%;
        height: 100%;
        border-radius: 10px;
      }
    }

    .lyric-info {
      flex: 1;
      min-width: 0;

      .name {
        font-size: 17px;
        font-weight: 700;
      }

      .artist {
        margin-top: 4px;
        font-size: 13px;
        opacity: 0.62;
      }
    }

    .lyric-header-tip {
      font-size: 12px;
      opacity: 0.52;
      flex-shrink: 0;
    }
  }

  .lyric-main {
    flex: 1;
    min-height: 0;
  }

  .meta-stage {
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: 18px;
    padding-top: 12px;
  }

  .song-info-bar {
    display: flex;
    justify-content: space-between;
    gap: 12px;

    .info-section {
      flex: 1;
      min-width: 0;

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
      gap: 10px;
      padding-top: 8px;
      flex-shrink: 0;
    }
  }

  .action-btn {
    width: 40px;
    height: 40px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    cursor: pointer;
    transition: background-color 0.2s;

    &:active {
      background-color: rgba(255, 255, 255, 0.08);
    }

    .n-icon {
      color: rgb(var(--main-cover-color));
      opacity: 0.68;

      &.liked {
        fill: rgb(var(--main-cover-color));
        opacity: 1;
      }
    }
  }

  .progress-section {
    display: flex;
    align-items: center;

    .time {
      width: 42px;
      text-align: center;
      font-size: 12px;
      opacity: 0.6;
      color: rgb(var(--main-cover-color));
      font-variant-numeric: tabular-nums;
    }

    .n-slider {
      margin: 0 12px;
    }
  }

  .control-section {
    width: 100%;
    max-width: 420px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 10px;

    .placeholder {
      width: 24px;
    }

    .mode-btn,
    .ctrl-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }

    .mode-btn {
      width: 40px;
      height: 40px;

      .n-icon {
        color: rgb(var(--main-cover-color));
      }
    }

    .ctrl-btn {
      width: 50px;
      height: 50px;

      .n-icon {
        color: rgb(var(--main-cover-color));
      }
    }

    .play-btn {
      width: 62px;
      height: 62px;
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: rgba(var(--main-cover-color), 0.2);
      color: rgb(var(--main-cover-color));

      &.n-button--primary-type {
        --n-color: rgba(var(--main-cover-color), 0.14);
        --n-color-hover: rgba(var(--main-cover-color), 0.2);
        --n-color-focus: rgba(var(--main-cover-color), 0.2);
        --n-color-pressed: rgba(var(--main-cover-color), 0.12);
      }
    }
  }

  @media (max-height: 820px) {
    --mobile-cover-size: min(68vw, 32vh);

    .meta-stage {
      gap: 14px;
    }

    .song-info-bar {
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
  }
}

.comment-header {
  display: flex;
  flex-direction: column;

  .count {
    margin-top: 8px;
    font-size: 12px;
  }
}
</style>

<style lang="scss">
.mobile-player-comment {
  --n-border-radius: 24px 24px 0 0;

  .n-drawer-header {
    min-height: 88px;
    padding-top: calc(var(--safe-area-inset-top) + 16px);
    align-items: flex-end;
  }

  .n-drawer-body-content-wrapper {
    padding-bottom: 0;
  }

  .n-drawer-body-content {
    padding: 0 16px calc(var(--safe-area-inset-bottom) + 16px);
  }
}

.full-player-comment {
  --n-color: rgb(var(--main-cover-color));
  --n-close-icon-color: rgba(var(--main-cover-color), 0.58);
  background-color: transparent;
  box-shadow: none;

  .n-drawer-header {
    border: none;
  }

  .n-drawer-body-content {
    background-color: rgba(var(--main-cover-color), 0.04);
  }

  a,
  span,
  .n-icon {
    color: rgb(var(--main-cover-color));
  }
}
</style>
