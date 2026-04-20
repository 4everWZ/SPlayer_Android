package top.imsyy.splayer.nativeapp.ui.navigation

object Routes {
    const val Home = "home"
    const val Discovery = "discovery"
    const val Podcast = "podcast"
    const val My = "my"
    const val Search = "search"
    const val Player = "player"
    const val Settings = "settings"
    const val Playlist = "playlist"
    const val Album = "album"
    const val Radio = "radio"
    const val SearchKeywordArg = "keyword"
    const val MyTabArg = "tab"
    const val SearchRoute = "$Search?$SearchKeywordArg={$SearchKeywordArg}"
    const val MyRoute = "$My?$MyTabArg={$MyTabArg}"
    const val PlaylistIdArg = "playlistId"
    const val AlbumIdArg = "albumId"
    const val RadioIdArg = "radioId"

    fun playlistDetail(playlistId: Long): String = "$Playlist/$playlistId"
    fun albumDetail(albumId: Long): String = "$Album/$albumId"
    fun radioDetail(radioId: Long): String = "$Radio/$radioId"
    fun search(keyword: String? = null): String = if (keyword.isNullOrBlank()) Search else "$Search?$SearchKeywordArg=${android.net.Uri.encode(keyword)}"
    fun my(tab: String? = null): String = if (tab.isNullOrBlank()) My else "$My?$MyTabArg=$tab"
}
