package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.EventInterface
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val ChatBubbleShapeMe = RoundedCornerShape(15.dp, 15.dp, 3.dp, 15.dp)
val ChatBubbleShapeThem = RoundedCornerShape(3.dp, 15.dp, 15.dp, 15.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatroomMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    navController: NavController,
    onWantsToReply: (Note) -> Unit
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return
    val loggedIn = remember(accountState) { accountState?.account?.userProfile() } ?: return

    val noteState by baseNote.live().metadata.observeAsState()
    val note = remember(noteState) { noteState?.note }

    val noteReportsState by baseNote.live().reports.observeAsState()
    val noteForReports = remember(noteReportsState) { noteReportsState?.note } ?: return

    val noteEvent = note?.event

    var popupExpanded by remember { mutableStateOf(false) }

    if (noteEvent == null) {
        BlankNote(Modifier.combinedClickable(
            onClick = { },
            onLongClick = { popupExpanded = true }
        ))

        note?.let {
            NoteQuickActionMenu(it, popupExpanded, { popupExpanded = false }, accountViewModel)
        }
    } else {
        var showHiddenNote by remember { mutableStateOf(false) }
        var isAcceptableAndCanPreview by remember { mutableStateOf(Pair(true, true)) }

        LaunchedEffect(key1 = noteReportsState, key2 = accountState) {
            withContext(Dispatchers.IO) {
                account.userProfile().let { loggedIn ->
                    val newCanPreview = note.author === loggedIn ||
                            (note.author?.let { loggedIn.isFollowingCached(it) } ?: true) ||
                            !(noteForReports.hasAnyReports())

                    val newIsAcceptable = account.isAcceptable(noteForReports)

                    if (newIsAcceptable != isAcceptableAndCanPreview.first && newCanPreview != isAcceptableAndCanPreview.second) {
                        isAcceptableAndCanPreview = Pair(newIsAcceptable, newCanPreview)
                    }
                }
            }
        }

        if (!isAcceptableAndCanPreview.first && !showHiddenNote) {
            if (!account.isHidden(noteForReports.author!!)) {
                HiddenNote(
                    account.getRelevantReports(noteForReports),
                    account.userProfile(),
                    Modifier,
                    innerQuote,
                    navController,
                    onClick = { showHiddenNote = true }
                )
            }

        } else {
            val backgroundBubbleColor: Color
            val alignment: Arrangement.Horizontal
            val shape: Shape

            if (note.author == loggedIn) {
                backgroundBubbleColor = MaterialTheme.colors.primary.copy(alpha = 0.32f)
                    .compositeOver(parentBackgroundColor ?: MaterialTheme.colors.background)

                alignment = Arrangement.End
                shape = ChatBubbleShapeMe
            } else {
                backgroundBubbleColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                    .compositeOver(parentBackgroundColor ?: MaterialTheme.colors.background)

                alignment = Arrangement.Start
                shape = ChatBubbleShapeThem
            }

            val scope = rememberCoroutineScope()

            LaunchedEffect(key1 = routeForLastRead) {
                routeForLastRead?.let {
                    scope.launch(Dispatchers.IO) {
                        val lastTime = NotificationCache.load(it)

                        val createdAt = note.createdAt()
                        if (createdAt != null) {
                            NotificationCache.markAsRead(it, createdAt)
                        }
                    }
                }
            }

            Column() {
                val modif = remember {
                    if (innerQuote) {
                        Modifier.padding(top = 10.dp, end = 5.dp)
                    } else {
                        Modifier
                            .fillMaxWidth(1f)
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 5.dp,
                                bottom = 5.dp
                            )
                    }
                }

                Row(
                    modifier = modif,
                    horizontalArrangement = alignment
                ) {
                    var availableBubbleSize by remember { mutableStateOf(IntSize.Zero) }
                    val modif2 = if (innerQuote) Modifier else Modifier.fillMaxWidth(0.85f)

                    Row(
                        horizontalArrangement = alignment,
                        modifier = modif2.onSizeChanged {
                            availableBubbleSize = it
                        }
                    ) {
                        Surface(
                            color = backgroundBubbleColor,
                            shape = shape,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { popupExpanded = true }
                                )
                        ) {
                            var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

                            Column(
                                modifier = Modifier
                                    .padding(start = 10.dp, end = 5.dp, bottom = 5.dp)
                                    .onSizeChanged {
                                        bubbleSize = it
                                    }
                            ) {
                                if ((innerQuote || note.author != loggedIn) && noteEvent is ChannelMessageEvent) {
                                    DrawAuthorInfo(
                                        baseNote,
                                        alignment,
                                        navController
                                    )
                                }

                                val replyTo = note.replyTo
                                if (!innerQuote && !replyTo.isNullOrEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        replyTo.toSet().mapIndexed { _, note ->
                                            ChatroomMessageCompose(
                                                note,
                                                null,
                                                innerQuote = true,
                                                parentBackgroundColor = backgroundBubbleColor,
                                                accountViewModel = accountViewModel,
                                                navController = navController,
                                                onWantsToReply = onWantsToReply
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (noteEvent) {
                                        is ChannelCreateEvent -> {
                                            RenderCreateChannelNote(note)
                                        }

                                        is ChannelMetadataEvent -> {
                                            RenderChangeChannelMetadataNote(note)
                                        }

                                        else -> {
                                            RenderRegularTextNote(
                                                note,
                                                loggedIn,
                                                isAcceptableAndCanPreview.second,
                                                backgroundBubbleColor,
                                                accountViewModel,
                                                navController
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .then(
                                            with(LocalDensity.current) {
                                                Modifier.widthIn(
                                                    bubbleSize.width.toDp(),
                                                    availableBubbleSize.width.toDp()
                                                )
                                            }
                                        )
                                ) {
                                    StatusRow(
                                        baseNote,
                                        accountViewModel,
                                        onWantsToReply
                                    )
                                }
                            }
                        }
                    }

                    NoteQuickActionMenu(
                        note,
                        popupExpanded,
                        { popupExpanded = false },
                        accountViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    onWantsToReply: (Note) -> Unit
) {
    val grayTint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
    val time = remember { baseNote.createdAt() ?: 0 }

    Row() {
        ChatTimeAgo(time)
        RelayBadges(baseNote)
        Spacer(modifier = Modifier.width(10.dp))
    }

    Row() {
        LikeReaction(baseNote, grayTint, accountViewModel)
        Spacer(modifier = Modifier.width(5.dp))
        ZapReaction(baseNote, grayTint, accountViewModel)
        Spacer(modifier = Modifier.width(5.dp))
        ReplyReaction(baseNote, grayTint, accountViewModel, showCounter = false) {
            onWantsToReply(baseNote)
        }
    }
}

@Composable
fun ChatTimeAgo(time: Long) {
    val context = LocalContext.current

    var timeStr by remember { mutableStateOf("") }

    LaunchedEffect(key1 = time) {
        withContext(Dispatchers.IO) {
            timeStr = timeAgoShort(time, context = context)
        }
    }

    Text(
        timeStr,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
        fontSize = 12.sp
    )
}

@Composable
private fun RenderRegularTextNote(
    note: Note,
    loggedIn: User,
    canPreview: Boolean,
    backgroundBubbleColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val tags = remember { note.event?.tags() }
    val eventContent = remember { accountViewModel.decrypt(note) }
    val modifier = remember { Modifier.padding(top = 5.dp) }

    if (eventContent != null) {
        TranslatableRichTextViewer(
            eventContent,
            canPreview,
            modifier,
            tags,
            backgroundBubbleColor,
            accountViewModel,
            navController
        )
    } else {
        TranslatableRichTextViewer(
            stringResource(R.string.could_not_decrypt_the_message),
            true,
            modifier,
            tags,
            backgroundBubbleColor,
            accountViewModel,
            navController
        )
    }
}

@Composable
private fun RenderChangeChannelMetadataNote(
    note: Note
) {
    val noteEvent = note.event as? ChannelMetadataEvent ?: return

    val channelInfo = noteEvent.channelInfo()
    val text = note.author?.toBestDisplayName()
        .toString() + " ${stringResource(R.string.changed_chat_name_to)} '" + (
            channelInfo.name
                ?: ""
            ) + "', ${stringResource(R.string.description_to)} '" + (
            channelInfo.about
                ?: ""
            ) + "', ${stringResource(R.string.and_picture_to)} '" + (
            channelInfo.picture
                ?: ""
            ) + "'"

    CreateTextWithEmoji(
        text = text,
        tags = note.author?.info?.latestMetadata?.tags
    )
}

@Composable
private fun RenderCreateChannelNote(note: Note) {
    val noteEvent = note.event as? ChannelCreateEvent ?: return
    val channelInfo = remember { noteEvent.channelInfo() }

    val text = note.author?.toBestDisplayName()
        .toString() + " ${stringResource(R.string.created)} " + (
            channelInfo.name
                ?: ""
            ) + " ${stringResource(R.string.with_description_of)} '" + (
            channelInfo.about
                ?: ""
            ) + "', ${stringResource(R.string.and_picture)} '" + (
            channelInfo.picture
                ?: ""
            ) + "'"

    CreateTextWithEmoji(
        text = text,
        tags = note.author?.info?.latestMetadata?.tags
    )
}

@Composable
private fun DrawAuthorInfo(
    baseNote: Note,
    alignment: Arrangement.Horizontal,
    navController: NavController
) {
    val userState by baseNote.author!!.live().metadata.observeAsState()

    val pubkeyHex = remember { baseNote.author?.pubkeyHex } ?: return
    val route = remember { "User/$pubkeyHex" }
    val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
    val userProfilePicture = remember(userState) { ResizeImage(userState?.user?.profilePicture(), 25.dp) }
    val userTags = remember(userState) { userState?.user?.info?.latestMetadata?.tags }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = alignment,
        modifier = Modifier.padding(top = 5.dp)
    ) {
        RobohashAsyncImageProxy(
            robot = pubkeyHex,
            model = userProfilePicture,
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = Modifier
                .width(25.dp)
                .height(25.dp)
                .clip(shape = CircleShape)
                .clickable(onClick = {
                    navController.navigate(route)
                })
        )

        CreateClickableTextWithEmoji(
            clickablePart = "  $userDisplayName",
            suffix = "",
            tags = userTags,
            fontWeight = FontWeight.Bold,
            overrideColor = MaterialTheme.colors.onBackground,
            route = route,
            navController = navController
        )
    }
}

@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.live().relays.observeAsState()
    val noteRelays = remember(noteRelaysState) { noteRelaysState?.note?.relays ?: emptySet() }
    val noteRelaysSimple = remember(noteRelaysState) { noteRelaysState?.note?.relays?.take(3) ?: emptySet() }

    var expanded by remember { mutableStateOf(false) }

    val relaysToDisplay by remember {
        derivedStateOf {
            if (expanded) noteRelays else noteRelaysSimple
        }
    }

    FlowRow(Modifier.padding(start = 10.dp)) {
        relaysToDisplay.forEach {
            RenderRelay(it)
        }

        if (noteRelays.size > 3 && !expanded) {
            IconButton(
                modifier = Modifier.then(Modifier.size(15.dp)),
                onClick = { expanded = true }
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }
        }
    }
}

@Composable
private fun RenderRelay(dirtyUrl: String) {
    val uri = LocalUriHandler.current
    val website = remember {
        val cleanUrl = dirtyUrl.removePrefix("wss://").removePrefix("ws://")
        "https://$cleanUrl"
    }
    val iconUrl = remember {
        val cleanUrl = dirtyUrl.removePrefix("wss://").removePrefix("ws://")
        "https://$cleanUrl/favicon.ico"
    }

    Box(
        remember {
            Modifier
                .size(15.dp)
                .padding(1.dp)
        }
    ) {
        RobohashFallbackAsyncImage(
            robot = iconUrl,
            robotSize = 15.dp,
            model = iconUrl,
            contentDescription = stringResource(id = R.string.relay_icon),
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            modifier = Modifier
                .fillMaxSize(1f)
                .clip(shape = CircleShape)
                .background(MaterialTheme.colors.background)
                .clickable(onClick = { uri.openUri(website) })
        )
    }
}
