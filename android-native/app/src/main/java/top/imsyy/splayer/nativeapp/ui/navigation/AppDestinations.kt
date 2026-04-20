package top.imsyy.splayer.nativeapp.ui.navigation

enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    Recommend(Routes.Home, "推荐"),
    Discovery(Routes.Discovery, "发现"),
    My(Routes.My, "我的"),
}

enum class DrawerEntry(val label: String) {
    LikedSongs("喜欢的音乐"),
    RecentPlays("最近播放"),
    CollectedPlaylists("收藏歌单"),
    Settings("设置"),
}
