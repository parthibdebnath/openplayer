package io.github.parthibdebnath.openplayer.ui.skin

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.parthibdebnath.openplayer.audio.OutputDevice
import io.github.parthibdebnath.openplayer.audio.PlaybackUiState
import io.github.parthibdebnath.openplayer.data.model.Song
import io.github.parthibdebnath.openplayer.data.settings.UiTheme
import io.github.parthibdebnath.openplayer.ui.components.ContextMenuItem
import io.github.parthibdebnath.openplayer.ui.components.GlassRole
import io.github.parthibdebnath.openplayer.ui.theme.AppColors

/**
 * The skin in effect, or null for openplayer's own look. Every shared control - text, buttons,
 * panels, switchers, sliders, toggles, menus, dialogs, the header and players, the tab bar - asks
 * this first, so a skin restyles every screen without the screens knowing about it.
 */
val LocalSkin = staticCompositionLocalOf<Skin?> { null }

/** What a button is for, where a skin draws some kinds differently (a gel Play pill, a green Go). */
enum class ButtonKind { Plain, Play, Previous, Next, Confirm, Toggle, Chevron }

/** The frame around the screens: the tabs, and what's playing for frames that show it. */
@Stable
class FrameModel(
    val tabs: List<String>,
    val icons: List<ImageVector>,
    val selectedTab: Int,
    val onSelectTab: (Int) -> Unit,
    val playback: PlaybackUiState,
    val twentyFourHour: Boolean,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    /** Opens Settings (on the File tab), for frames with a menu or softkey of their own. */
    val onOpenSettings: () -> Unit,
    /** Swaps the upcoming song in this slot for the next available one. */
    val onSwapUpcoming: (Int) -> Unit = {},
)

/** Everything the Play screen shows and does, for a skin that lays it out its own way. */
@Stable
class NowPlayingModel(
    val playback: PlaybackUiState,
    val playbackRestored: Boolean,
    val progress: Float,
    val onSeek: (Float) -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val shuffleOn: Boolean,
    val onToggleShuffle: () -> Unit,
    /** Null when the song can't be a favourite (nothing loaded, or a file from another app). */
    val isFavourite: Boolean?,
    val onToggleFavourite: () -> Unit,
    val outputDevice: OutputDevice,
    val outputSwitchAvailable: Boolean,
    val onToggleOutput: () -> Unit,
    val sleepTimerActive: Boolean,
    val fxOn: Boolean,
    val shuffleLocked: Boolean,
    val twentyFourHour: Boolean,
    val onOpenLibrary: () -> Unit,
    val onHoldTitle: () -> Unit,
    val onOpenSleepTimer: () -> Unit,
    /** An error or notice about playback, worded for the listener. */
    val message: String?,
    val upcomingExpanded: Boolean,
    val onSetUpcomingExpanded: (Boolean) -> Unit,
    /** Swaps the upcoming song in this slot for the next available one. */
    val onSwapUpcoming: (Int) -> Unit,
    /** A song held: its tags are edited. */
    val onHoldSong: (Song) -> Unit,
    /** Changes the media volume by this many percent; used by skins with a wheel or rocker. */
    val onVolumeDelta: (Int) -> Int,
    /** The header, with the output-switch confirmation over it when one is pending. */
    val header: @Composable (Modifier) -> Unit,
    /** The sleep timer panel, while it's open; null otherwise. */
    val sleepTimerPanel: (@Composable (Modifier) -> Unit)?,
    /** The upcoming songs list (themed through the shared controls). */
    val upcoming: @Composable (Modifier) -> Unit,
) {
    val song: Song? get() = playback.currentSong
    val hasSong: Boolean get() = playback.currentSong != null
}

/** The mini player at the foot of the File and SFX screens. */
@Stable
class MiniPlayerModel(
    val song: Song?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onOpenSettings: (() -> Unit)?,
    val playbackRestored: Boolean,
    val onHoldTitle: ((Song) -> Unit)?,
)

/** One entry on the playlists screen: Favourites, a tag, or a playlist. */
@Stable
class CollectionEntry(
    val key: Any,
    val kind: Kind,
    /** The playlist's position, shown in its badge; null for Favourites and tags. */
    val number: Int?,
    val title: String,
    val subtitle: String,
    /** The one that's playing. */
    val emphasized: Boolean,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val longClickLabel: String,
    /** The entry's context menu, drawn where the entry is; empty while closed. */
    val menu: @Composable () -> Unit,
) {
    enum class Kind { Favourites, Tag, Playlist }
}

/** One song in a playlist's list. */
@Stable
class SongRowModel(
    /** Position in the list as shown, for striped rows. */
    val index: Int,
    val title: String,
    val artist: String,
    val isCurrent: Boolean,
    val unreadable: Boolean,
    val titleScrollRequest: Int,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
    val menu: @Composable () -> Unit,
)

/** How a slider's track and thumb are drawn. Touch handling stays the same in every skin. */
interface SliderLook {
    val height: Dp
    /** The thumb's width; the thumb's centre travels between half of this and width minus half. */
    val thumbWidth: Dp
    fun DrawScope.drawSlider(fraction: Float, enabled: Boolean, steps: Int, pressed: Boolean)
}

/**
 * A complete look for the app. Defaults fall back to openplayer's own controls, so a skin only
 * overrides what it changes - though every finished skin overrides nearly everything.
 */
abstract class Skin {
    abstract val theme: UiTheme

    /** The page and content colours inside the frame; replaces the user's background colour. */
    abstract val colors: AppColors

    /** The skin's type, for one of the app's text sizes (12-20 sp). */
    abstract fun textStyle(sizeSp: Int, bold: Boolean, density: Density): TextStyle

    /** The whole frame: background, chrome, tabs. [content] is the current screen. */
    @Composable
    abstract fun Frame(model: FrameModel, content: @Composable (Modifier) -> Unit)

    /** The clock and the playing playlist's name, at the top of each screen. */
    @Composable
    open fun Header(
        playlistLabel: String,
        twentyFourHour: Boolean,
        onOpenSleepTimer: (() -> Unit)?,
        overlay: (@Composable BoxScope.() -> Unit)?,
        modifier: Modifier,
    ) = io.github.parthibdebnath.openplayer.ui.components.DefaultTopHeader(playlistLabel, twentyFourHour, modifier, onOpenSleepTimer, overlay)

    @Composable
    abstract fun NowPlaying(model: NowPlayingModel)

    @Composable
    abstract fun MiniPlayer(model: MiniPlayerModel, modifier: Modifier)

    @Composable
    abstract fun IconButton(
        icon: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier,
        size: Dp,
        iconSize: Dp,
        opacity: Float,
        enabled: Boolean,
        kind: ButtonKind,
    )

    /** A panel's surface: dialogs, notifications, fields, badges. Applied inside `composed`. */
    @Composable
    abstract fun panel(modifier: Modifier, shape: Shape, role: GlassRole, opacity: Float): Modifier

    @Composable
    abstract fun Switcher(
        items: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier,
        height: Dp,
        fontSizeSp: Float,
        icons: List<ImageVector>?,
    )

    abstract val slider: SliderLook

    @Composable
    abstract fun ToggleRow(
        label: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit,
        modifier: Modifier,
        bold: Boolean,
        fontSizeSp: Int,
    )

    @Composable
    abstract fun Divider(modifier: Modifier)

    @Composable
    open fun Scrollbar(state: LazyListState, modifier: Modifier) =
        io.github.parthibdebnath.openplayer.ui.components.DefaultListScrollbar(state, modifier, 0.35f)

    /** The inside of a popup menu. Call [onPick] with the item chosen. */
    @Composable
    abstract fun Menu(items: List<ContextMenuItem>, onPick: (ContextMenuItem) -> Unit)

    @Composable
    abstract fun Notification(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier)

    /** A text field's box, drawn behind its text. */
    @Composable
    abstract fun textField(modifier: Modifier, focused: Boolean): Modifier

    /** The text colour inside a text field, where the field isn't the page's own colour. */
    open fun textFieldInk(focused: Boolean): Color? = null

    /** Favourites, tags and playlists on the playlists screen. */
    @Composable
    abstract fun Collection(entries: List<CollectionEntry>, emptyMessage: String?, modifier: Modifier)

    /** One song row; [modifier] carries the row's fixed height. */
    @Composable
    abstract fun SongRow(model: SongRowModel, modifier: Modifier)

    /** Around a list of songs - a sunken well, a card, or nothing. */
    @Composable
    open fun ListWell(modifier: Modifier, content: @Composable BoxScope.() -> Unit) =
        androidx.compose.foundation.layout.Box(modifier, content = content)

    /** Gutter the screens leave at their sides inside this skin's frame. */
    open val gutter: Dp = 18.dp

    /** Whether a sideways swipe moves between tabs, as it does in openplayer's own look. */
    open val swipesBetweenTabs: Boolean = true

    /** Whether screens show [Header] at all; a frame with its own clock and title can drop it. */
    open val showsHeader: Boolean = true

    /** Whether the File and SFX screens show [MiniPlayer]; a frame with its own transport drops it. */
    open val showsMiniPlayer: Boolean = true

}
