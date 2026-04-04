<template>
  <div class="search-type">
    <Transition name="fade" mode="out-in">
      <CoverList
        v-if="searchCount > 0"
        :data="searchResultData"
        :loading="loading"
        :loadMore="hasMore"
        type="album"
        :hiddenCover="settingStore.hiddenCovers.album"
        @loadMore="loadMore"
      />
      <n-result
        v-else-if="errorMessage"
        status="warning"
        :description="errorMessage"
        style="margin-top: 60px"
      >
        <template #icon>
          <SvgIcon name="SearchOff" />
        </template>
        <template #footer>
          <n-button strong secondary @click="resetSearch">重新加载</n-button>
        </template>
      </n-result>
      <n-empty
        v-else
        :description="`很抱歉，未能找到与 ${keyword} 相关的任何专辑`"
        style="margin-top: 60px"
        size="large"
      >
        <template #icon>
          <SvgIcon name="SearchOff" />
        </template>
      </n-empty>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import type { CoverType } from "@/types/main";
import { searchResult } from "@/api/search";
import { formatCoverList } from "@/utils/format";
import { useSettingStore } from "@/stores";

const props = defineProps<{
  keyword: string;
}>();

const settingStore = useSettingStore();

// 搜索数据
const hasMore = ref<boolean>(true);
const loading = ref<boolean>(true);
const searchOffset = ref<number>(0);
const searchCount = ref<number>(1);
const searchResultData = ref<CoverType[]>([]);
const errorMessage = ref<string>("");

// 获取搜索结果
const getSearchResult = async () => {
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await searchResult(props.keyword, 50, searchOffset.value, 10);
    const payload = result?.result;
    if (!payload) {
      hasMore.value = false;
      searchCount.value = 0;
      searchResultData.value = [];
      errorMessage.value = "搜索失败，请检查网络后重试";
      return;
    }
    hasMore.value = payload.hasMore || payload.albumCount > searchOffset.value + 50;
    searchCount.value = payload.albumCount || 0;
    const albumData = formatCoverList(payload.albums || []);
    searchResultData.value = searchResultData.value.concat(albumData);
  } catch (error) {
    console.error("搜索专辑失败:", error);
    hasMore.value = false;
    searchCount.value = 0;
    searchResultData.value = [];
    errorMessage.value = "搜索失败，请检查网络后重试";
  } finally {
    loading.value = false;
  }
};

// 加载更多
const loadMore = () => {
  if (!hasMore.value || loading.value) return;
  searchOffset.value += 50;
  void getSearchResult();
};

const resetSearch = () => {
  searchOffset.value = 0;
  hasMore.value = true;
  searchCount.value = 1;
  searchResultData.value = [];
  void getSearchResult();
};

watch(
  () => props.keyword,
  () => {
    resetSearch();
  },
  { immediate: true },
);
</script>
