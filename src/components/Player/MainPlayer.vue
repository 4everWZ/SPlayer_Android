<template>
  <div
    ref="playerRef"
    :class="[
      'main-player',
      {
        show: musicStore.isHasPlayer && statusStore.showPlayBar,
        player: statusStore.showFullPlayer,
        mobile: isSmallScreen,
      },
    ]"
  >
    <!-- 进度条 -->
    <PlayerSlider />
    <!-- 信息 -->
    <div
      :class="[
        'play-data',
        {
          'hidden-cover': settingStore.hiddenCovers.player,
        },
      ]"
    >
      <!-- 封面 -->
      <Transition name="fade">
        <div
          v-if="!settingStore.hiddenCovers.player"
          :key="musicStore.playSong.cover"
          class="cover"
          @click.stop="statusStore.showFullPlayer = true"
        >
          <n-image
            :src="musicStore.songCover"
            :alt="musicStore.songCover"
            class="cover-img"
            preview-disabled
            @load="coverLoaded"
          >
            <template #placeholder>
              <div class="cover-loading">
                <img src="/images/song.jpg?asset" class="loading-img" alt="loading-img" />
              </div>
            </template>
          </n-image>
          <!-- 打开播放器 -->
          <SvgIcon name="Expand" :size="30" />
        </div>
      </Transition>
      <!-- 信息 -->
      <Transition name="left-sm" mode="out-in">
        <div :key="musicStore.playSong.id" class="info">
          <div class="data">
            <!-- 名称 -->
            <TextContainer
              :key="musicStore.playSong.name"
              :text="playerTitleText"
              :speed="0.2"
              class="name"
              style="cursor: pointer"
              @click.stop="settingStore.hiddenCovers.player && openFullPlayerFromBar()"
            />
            <!-- 倍速 -->
            <n-tag
              v-if="statusStore.playRate !== 1 && !isSmallScreen"
              type="primary"
              size="small"
              round
              @click.stop="openChangeRate"
            >
              {{ statusStore.playRate }}x
            </n-tag>
            <!-- 喜欢 -->
            <SvgIcon
              v-if="musicStore.playSong.type !== 'radio' && !isSmallScreen"
              :name="dataStore.isLikeSong(musicStore.playSong.id) ? 'Favorite' : 'FavoriteBorder'"
              :size="20"
              class="like"
              @click.stop="
                toLikeSong(musicStore.playSong, !dataStore.isLikeSong(musicStore.playSong.id))
              "
            />
            <!-- 更多操作 -->
            <n-dropdown
              v-if="!isSmallScreen"
              :options="songMoreOptions"
              trigger="click"
              placement="top-start"
            >
              <SvgIcon name="FormatList" :size="20" :depth="2" class="more" @click.stop />
            </n-dropdown>
          </div>
          <div class="lyric-container">
            <Transition
              :name="settingStore.lyricTransition === 'fade' ? 'fade' : 'lyric-slide'"
              :mode="settingStore.lyricTransition === 'fade' ? 'out-in' : undefined"
            >
              <!-- 歌词 -->
              <TextContainer
                v-if="isShowLyrics && instantLyrics"
                :key="instantLyrics"
                :text="instantLyrics"
                :speed="0.5"
                :delay="500"
                class="lyric"
              />
              <!-- 歌手 -->
              <div v-else class="artists">
                <TextContainer :speed="0.5" class="artists-container">
                  <n-text
                    v-if="musicStore.playSong.type === 'radio'"
                    class="ar-item"
                    @click.stop="showCreatorTip"
                  >
                    {{ musicStore.playSong.dj?.creator || "未知艺术家" }}
                  </n-text>
                  <template v-else-if="Array.isArray(musicStore.playSong.artists)">
                    <n-text
                      v-for="(item, index) in musicStore.playSong.artists"
                      :key="index"
                      class="ar-item"
                      @click.stop="openJumpArtist(musicStore.playSong.artists, item.id)"
                    >
                      {{
                        settingStore.hideBracketedContent ? removeBrackets(item.name) : item.name
                      }}
                    </n-text>
                  </template>
                  <n-text
                    v-else
                    class="ar-item"
                    @click.stop="openJumpArtist(musicStore.playSong.artists)"
                  >
                    {{
                      settingStore.hideBracketedContent
                        ? removeBrackets(musicStore.playSong.artists)
                        : musicStore.playSong.artists || "未知艺术家"
                    }}
                  </n-text>
                </TextContainer>
              </div>
            </Transition>
          </div>
        </div>
      </Transition>
    </div>
    <!-- 控制 -->
    <n-flex :size="8" align="center" justify="center" class="play-control" @click.stop>
      <!-- 播放模式循环（左侧） -->
      <template
        v-if="musicStore.playSong.type !== 'radio' && !statusStore.personalFmMode && !isSmallScreen"
      >
        <div class="play-icon mode-icon" @click.stop="player.cyclePlayMode()">
          <SvgIcon
            :name="statusStore.shuffleIcon"
            :size="20"
            :depth="statusStore.playerModeKey === 'repeat-off' ? 3 : 1"
          />
        </div>
      </template>
      <!-- 不喜欢 -->
      <div
        v-if="statusStore.personalFmMode"
        class="play-icon"
        v-debounce="
          () =>
            songManager.personalFMTrash(musicStore.personalFMSong?.id, () =>
              player.nextOrPrev('next'),
            )
        "
      >
        <SvgIcon class="icon" :size="18" name="ThumbDown" />
      </div>
      <!-- 上一曲 -->
      <div
        v-else-if="!isSmallScreen"
        class="play-icon nav-icon"
        v-debounce="() => player.nextOrPrev('prev')"
      >
        <SvgIcon :size="26" name="SkipPrev" />
      </div>
      <!-- 播放暂停 -->
      <n-button
        :loading="statusStore.playLoading"
        :focusable="false"
        :keyboard="false"
        class="play-pause"
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
              :size="28"
            />
          </Transition>
        </template>
      </n-button>
      <!-- 下一曲 -->
      <div
        v-if="!isSmallScreen"
        class="play-icon nav-icon"
        v-debounce="() => player.nextOrPrev('next')"
      >
        <SvgIcon :size="26" name="SkipNext" />
      </div>
      <!-- 桌面歌词开关（右侧） -->
      <n-badge
        v-if="isElectron && !isSmallScreen"
        value="ON"
        :show="statusStore.showDesktopLyric"
      >
        <div class="play-icon mode-icon" @click.stop="player.toggleDesktopLyric()">
          <SvgIcon
            name="DesktopLyric2"
            :size="20"
            :depth="statusStore.showDesktopLyric ? 1 : 3"
          />
        </div>
      </n-badge>
    </n-flex>
    <!-- 功能 -->
    <Transition name="fade" mode="out-in">
      <n-flex
        :key="statusStore.personalFmMode ? 'fm' : 'normal'"
        :size="[8, 0]"
        class="play-menu"
        justify="end"
        @click.stop
      >
        <!-- 时间相关 -->
        <Transition name="fade" mode="out-in">
          <n-flex
            :key="statusStore.autoClose.enable ? 'autoClose' : 'time'"
            :size="4"
            justify="center"
            class="time-container"
            vertical
          >
            <div class="time" @click="toggleTimeFormat">
              <n-text depth="2">{{ timeDisplay[0] }}</n-text>
              <n-text depth="2">{{ timeDisplay[1] }}</n-text>
            </div>
            <!-- 定时关闭 -->
            <n-tag
              v-if="statusStore.autoClose.enable"
              size="small"
              type="primary"
              round
              @click="openAutoClose"
            >
              {{ convertSecondsToTime(statusStore.autoClose.remainTime) }}
              <template #icon>
                <SvgIcon name="TimeAuto" />
              </template>
            </n-tag>
          </n-flex>
        </Transition>
        <!-- 功能区 -->
        <PlayerRightMenu compact-mobile />
      </n-flex>
    </Transition>
    <n-drawer
      v-model:show="showSongMoreDrawer"
      placement="bottom"
      class="player-song-drawer"
      height="auto"
    >
      <n-drawer-content
        title="更多操作"
        :native-scrollbar="false"
        :body-content-style="{ padding: '0 16px calc(var(--safe-area-inset-bottom) + 16px)' }"
      >
        <n-flex vertical size="small" class="mobile-action-list">
          <n-button
            v-for="item in mobileSongMoreOptions"
            :key="String(item.key)"
            block
            strong
            secondary
            @click="handleSongMoreAction(item)"
          >
            {{ getSongMoreLabel(item) }}
          </n-button>
        </n-flex>
      </n-drawer-content>
    </n-drawer>
    <PlayerModePanel />
  </div>
</template>

<script setup lang="ts">
import { usePlayerController } from "@/core/player/PlayerController";
import { useSongManager } from "@/core/player/SongManager";
import { useMobile } from "@/composables/useMobile";
import { isElectron } from "@/utils/env";
import PlayerModePanel from "@/components/Player/PlayerModePanel.vue";
import { useDataStore, useMusicStore, useSettingStore, useStatusStore } from "@/stores";
import { toLikeSong } from "@/utils/auth";
import { useTimeFormat } from "@/composables/useTimeFormat";
import { useSwipe } from "@vueuse/core";
import { copyData, coverLoaded, renderIcon, shareResource } from "@/utils/helper";
import {
  openAutoClose,
  openChangeRate,
  openCopySongInfo,
  openDownloadSong,
  openJumpArtist,
  openPlaylistAdd,
} from "@/utils/modal";
import { convertSecondsToTime } from "@/utils/time";
import { removeBrackets } from "@/utils/format";
import type { DropdownOption } from "naive-ui";

const router = useRouter();
const dataStore = useDataStore();
const musicStore = useMusicStore();
const statusStore = useStatusStore();
const settingStore = useSettingStore();

const player = usePlayerController();
const songManager = useSongManager();
const { isSmallScreen } = useMobile();

const { timeDisplay, toggleTimeFormat } = useTimeFormat();

const playerRef = ref<HTMLElement | null>(null);
const showSongMoreDrawer = computed({
  get: () => statusStore.playerSongMenuOpen,
  set: (value: boolean) => {
    statusStore.playerSongMenuOpen = value;
  },
});

const openFullPlayerFromBar = () => {
  statusStore.showFullPlayer = true;
};


const playerTitleText = computed(() => {
  const songName = settingStore.hideBracketedContent
    ? removeBrackets(musicStore.playSong.name)
    : musicStore.playSong.name;

  if (!isSmallScreen.value) return songName;

  let artist = "";
  if (musicStore.playSong.type === "radio") {
    artist = musicStore.playSong.dj?.creator || "播客电台";
  } else if (Array.isArray(musicStore.playSong.artists)) {
    artist = musicStore.playSong.artists
      .map((item) => (settingStore.hideBracketedContent ? removeBrackets(item.name) : item.name))
      .join(" / ");
  } else {
    artist = settingStore.hideBracketedContent
      ? removeBrackets(musicStore.playSong.artists)
      : musicStore.playSong.artists || "";
  }

  return artist ? `${songName} - ${artist}` : songName;
});

// 触摸滑动切换歌曲
const { direction } = useSwipe(playerRef, {
  threshold: 50,
  onSwipeEnd: () => {
    if (direction.value === "left") {
      // 左滑
      player.nextOrPrev("next");
    } else if (direction.value === "right") {
      // 右滑
      player.nextOrPrev("prev");
    }
  },
});

// 歌曲更多操作
const songMoreOptions = computed<DropdownOption[]>(() => {
  // 当前状态
  const song = musicStore.playSong;
  const isHasMv = !!song?.mv && song.mv !== 0;
  const isSong = song.type === "song";
  const isLocal = !!song?.path;
  return [
    {
      key: "more",
      label: "更多操作",
      icon: renderIcon("Menu", { size: 18 }),
      children: [
        {
          key: "code-name",
          label: `复制${song.type === "song" ? "歌曲" : "节目"}名称`,
          props: {
            onClick: () => copyData(song.name),
          },
          icon: renderIcon("Copy", { size: 18 }),
        },
        {
          key: "code-id",
          label: `复制${song.type === "song" ? "歌曲" : "节目"} ID`,
          show: !isLocal,
          props: {
            onClick: () => copyData(song.id),
          },
          icon: renderIcon("Copy", { size: 18 }),
        },
        {
          key: "copy-song-info",
          label: "复制更多信息",
          show: !isLocal && isSong,
          props: {
            onClick: () => openCopySongInfo(song.id),
          },
          icon: renderIcon("FormatList", { size: 18 }),
        },
        {
          key: "share",
          label: `分享${song.type === "song" ? "歌曲" : "节目"}链接`,
          show: !isLocal,
          props: {
            onClick: () =>
              shareResource(song.type, song.id, {
                title: song.name,
                text: song.name,
                dialogTitle: `分享${song.type === "song" ? "歌曲" : "节目"}`,
              }),
          },
          icon: renderIcon("Share", { size: 18 }),
        },
      ],
    },
    {
      key: "search",
      label: "同名搜索",
      show: settingStore.useOnlineService,
      props: {
        onClick: () => router.push({ name: "search", query: { keyword: song.name } }),
      },
      icon: renderIcon("Search"),
    },
    {
      key: "line",
      type: "divider",
    },
    {
      key: "playlist-add",
      label: "添加到歌单",
      props: {
        onClick: () => openPlaylistAdd([song], isLocal),
      },
      icon: renderIcon("AddList"),
    },
    {
      key: "mv",
      label: "观看 MV",
      show: isSong && isHasMv,
      props: {
        onClick: () =>
          router.push({ name: "video", query: { id: musicStore.playSong.mv, type: "mv" } }),
      },
      icon: renderIcon("Video", { size: 18 }),
    },
    {
      key: "download",
      label: "下载歌曲",
      show: statusStore.isDeveloperMode && !isLocal && isSong,
      props: { onClick: () => openDownloadSong(musicStore.playSong) },
      icon: renderIcon("Download"),
    },
    {
      key: "wiki",
      label: "音乐百科",
      show: !isLocal && isSong,
      props: {
        onClick: () => router.push({ name: "song-wiki", query: { id: musicStore.playSong.id } }),
      },
      icon: renderIcon("Info"),
    },
    {
      key: "comment",
      label: "查看评论",
      show: !isLocal,
      props: {
        onClick: () => {
          const id = musicStore.playSong.id;
          const type = musicStore.playSong.type === "radio" ? 4 : 0;
          router.push({ name: "comment", query: { id, type } });
        },
      },
      icon: renderIcon("Message"),
    },
  ];
});

const mobileSongMoreOptions = computed<DropdownOption[]>(() => {
  const options: DropdownOption[] = [];
  songMoreOptions.value.forEach((item) => {
    if (item.show === false || item.type === "divider") return;
    if (item.children?.length) {
      item.children.forEach((child) => {
        if (child.show === false || child.type === "divider") return;
        options.push(child);
      });
      return;
    }
    options.push(item);
  });
  return options;
});

const getSongMoreLabel = (option: DropdownOption) => {
  return typeof option.label === "string" ? option.label : String(option.key || "");
};

const handleSongMoreAction = (option: DropdownOption) => {
  const onClick = option.props?.onClick;
  if (typeof onClick === "function") {
    (onClick as () => void)();
  }
  showSongMoreDrawer.value = false;
};

// 是否展示歌词
const isShowLyrics = computed(() => {
  const isHasLrc = musicStore.isHasLrc;
  return (
    isHasLrc &&
    !statusStore.lyricLoading &&
    settingStore.barLyricShow &&
    musicStore.playSong.type !== "radio" &&
    statusStore.playStatus &&
    statusStore.lyricIndex !== -1
  );
});

// 当前实时歌词
const instantLyrics = computed(() => {
  const isYrc = musicStore.songLyric.yrcData?.length && settingStore.showWordLyrics;
  const content = isYrc
    ? musicStore.songLyric.yrcData[statusStore.lyricIndex]
    : musicStore.songLyric.lrcData[statusStore.lyricIndex];
  const contentStr = content?.words?.map((v) => v.word).join("") || "";
  return content?.translatedLyric && settingStore.showTran
    ? `${contentStr}（ ${content?.translatedLyric} ）`
    : contentStr || "";
});

// 暂不支持查看主播主页
const showCreatorTip = () => window.$message.info("暂不支持查看主播主页");
</script>

<style lang="scss" scoped>
.main-player {
  position: fixed;
  left: 0;
  bottom: calc(-90px - var(--safe-area-inset-bottom));
  height: calc(80px + var(--safe-area-inset-bottom));
  padding: 0 15px var(--safe-area-inset-bottom) 15px;
  width: 100%;
  background-color: var(--surface-container-hex);
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  transition: bottom 0.3s;
  z-index: 10;
  &.show {
    bottom: 0;
  }
  .player-slider {
    position: absolute;
    width: 100%;
    height: 16px;
    top: -8px;
    left: 0;
    margin: 0;
    --n-rail-height: 3px;
    --n-handle-size: 14px;
  }
  .play-data {
    position: relative;
    display: flex;
    flex-direction: row;
    align-items: center;
    overflow: hidden;
    height: 100%;
    max-width: 640px;
    padding-left: 68px;
    cursor: pointer;
    .cover {
      position: absolute;
      display: flex;
      align-items: center;
      justify-content: center;
      left: 0;
      width: 56px;
      height: 56px;
      min-width: 56px;
      border-radius: 8px;
      overflow: hidden;
      margin-right: 12px;
      transition: opacity 0.2s;
      cursor: pointer;
      :deep(img) {
        width: 56px;
        height: 56px;
        opacity: 0;
        transition:
          transform 0.3s,
          opacity 0.3s,
          filter 0.3s;
      }
      .n-icon {
        position: absolute;
        color: #eee;
        opacity: 0;
        transform: scale(0.6);
        transition:
          opacity 0.3s,
          transform 0.3s;
      }
      &:hover {
        :deep(img) {
          transform: scale(1.2);
          filter: brightness(0.6) blur(2px);
        }
        .n-icon {
          opacity: 1;
          transform: scale(1);
        }
      }
      &:active {
        .n-icon {
          transform: scale(1.2);
        }
      }
    }
    .info {
      display: flex;
      flex-direction: column;
      flex: 1;
      min-width: 0;
      .data {
        display: flex;
        align-items: center;
        .name {
          font-weight: bold;
          font-size: 16px;
          flex: 0 1 auto;
          width: auto;
          min-width: 0;
          transition: color 0.3s;
        }
        .n-tag {
          margin-left: 8px;
          flex-shrink: 0;
        }
        .like {
          color: var(--primary-hex);
          margin-left: 8px;
          transition: transform 0.3s;
          cursor: pointer;
          flex-shrink: 0;
          &:hover {
            transform: scale(1.15);
          }
          &:active {
            transform: scale(1);
          }
        }
        .more {
          margin-left: 8px;
          cursor: pointer;
          flex-shrink: 0;
        }
      }
      .lyric-container {
        position: relative;
        height: 22px;
        margin-top: 2px;
        overflow: hidden;
        .lyric,
        .artists {
          margin-top: 0;
          position: absolute;
          top: 0;
          left: 0;
          width: 100%;
          height: 100%;
          display: flex;
          align-items: center;
        }
      }
      .artists {
        width: 100%;
        overflow: hidden;

        .artists-container {
          .ar-item {
            display: inline-flex;
            transition: color 0.3s;
            cursor: pointer;
            white-space: nowrap;

            &::after {
              content: "/";
              margin: 0 6px;
              opacity: 0.6;
              transition: none;
            }
            &:last-child {
              &::after {
                display: none;
              }
            }
            &:hover {
              color: var(--primary-hex);
              &::after {
                color: var(--n-close-icon-color);
              }
            }
          }
        }
      }
    }
    &.hidden-cover {
      padding-left: 0;
    }
  }
  .play-control {
    margin: 0 60px;
    .play-pause {
      --n-width: 44px;
      --n-height: 44px;
      margin: 0 4px;
      transition:
        background-color 0.3s,
        transform 0.3s;
      .n-icon {
        transition: opacity 0.1s ease-in-out;
      }
      &:hover {
        transform: scale(1.1);
      }
      &:active {
        transform: scale(1);
      }
    }
    .play-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      border-radius: 50%;
      will-change: transform;
      transition:
        background-color 0.3s,
        transform 0.3s;
      cursor: pointer;
      margin: 0 2px;
      .n-icon {
        color: var(--primary-hex);
      }
      &:hover {
        transform: scale(1.1);
        background-color: rgba(var(--primary), 0.16);
      }
      &:active {
        transform: scale(1);
      }
    }
  }
  .play-menu {
    margin-left: auto;
    max-width: 640px;
    .time-container {
      margin-right: 8px;
      .n-tag {
        justify-content: center;
        font-size: 12px;
      }
    }
    .time {
      cursor: pointer;
      display: flex;
      align-items: center;
      font-size: 12px;
      .n-text {
        color: var(--primary-hex);
        opacity: 0.8;
        &:nth-of-type(1) {
          &::after {
            content: "/";
            margin: 0 4px;
          }
        }
      }
      &:hover {
        text-decoration: underline;
        text-decoration-color: var(--primary-hex);
      }
    }
  }
  @media (max-width: 1024px) {
    .play-menu {
      .time-container {
        display: none !important;
      }
    }
  }
  @media (max-width: 810px) {
    left: 12px;
    width: calc(100% - 24px);
    bottom: calc(-92px - var(--safe-area-inset-bottom));
    height: calc(72px + var(--safe-area-inset-bottom));
    padding: 0 12px var(--safe-area-inset-bottom);
    background-color: rgba(var(--surface-container), 0.96);
    border-radius: 24px;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
    grid-template-columns: minmax(0, 1fr) auto auto;
    column-gap: 10px;
    .player-slider {
      display: none;
    }
    .play-control {
      margin: 0;
      .mode-icon {
        display: none;
      }
      .nav-icon {
        display: none;
      }
      .play-pause {
        --n-width: 46px;
        --n-height: 46px;
        margin: 0;
      }
    }
    .play-data {
      min-width: 0;
      padding-left: 58px;
      padding-right: 0;
      .cover {
        width: 46px;
        height: 46px;
        min-width: 46px;
        border-radius: 50%;
        :deep(img) {
          width: 46px;
          height: 46px;
          opacity: 1;
        }
        .n-icon {
          display: none;
        }
      }
      .info {
        justify-content: center;
        .data {
          .name {
            font-size: 15px;
            line-height: 1.35;
          }
          .like,
          .more,
          .n-tag {
            display: none;
          }
        }
        .lyric-container {
          display: none;
        }
      }
    }
    .play-menu {
      max-width: none;
      margin-left: 0;
      :deep(.right-menu) {
        gap: 0 !important;
        .menu-icon {
          padding: 10px;
        }
      }
    }
  }
}

.mobile-action-list {
  .n-button {
    justify-content: flex-start;
  }
}
</style>
