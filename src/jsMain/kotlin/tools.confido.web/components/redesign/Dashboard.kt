package tools.confido.web.components.redesign

import tools.confido.api.Api
import tools.confido.web.Client
import tools.confido.web.components.*
import tools.confido.web.components.redesign.basic.*
import tools.confido.web.components.redesign.calibration.CalibrationGraphContent
import tools.confido.web.components.redesign.calibration.CalibrationReqView
import tools.confido.web.components.redesign.calibration.CalibrationView
import tools.confido.web.components.redesign.forms.*
import tools.confido.web.components.redesign.layout.LayoutMode
import tools.confido.web.components.redesign.layout.LayoutModeContext
import tools.confido.web.components.redesign.questions.*
import tools.confido.web.components.redesign.rooms.*
import tools.confido.web.components.redesign.search.searchUrl
import web.cssom.*
import emotion.react.*
import tools.confido.web.hooks.useSuspendResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import tools.confido.payloads.requests.CalibrationRequest
import tools.confido.payloads.responses.MyScoresResponse
import tools.confido.payloads.requests.Myself
import react.*
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.header
import react.dom.html.ReactHTML.main
import react.dom.html.ReactHTML.span
import react.router.dom.Link
import react.router.useNavigate
import web.html.HTMLDivElement
import kotlinx.datetime.Clock
import tools.confido.calibration.CalibrationVector
import tools.confido.payloads.requests.SetUiSetting
import tools.confido.payloads.responses.DashboardItem
import tools.confido.payloads.responses.DashboardModerationKind
import tools.confido.question.Prediction
import tools.confido.web.components.redesign.EyeIcon
import tools.confido.web.components.redesign.EyeOffIcon
import tools.confido.payloads.responses.WSData
import tools.confido.state.Topics
import tools.confido.utils.toFixed
import tools.confido.utils.stringToColor
import tools.confido.web.hooks.useSubscription
import tools.confido.users.User
import tools.confido.web.utils.runCoroutine

internal fun ChildrenBuilder.title(text: String, sidePad: Length) = div {
    css {
        padding = Padding(24.px, sidePad, 0.px)
        fontFamily = sansSerif
        fontSize = 14.px
        lineHeight = 17.px
        fontWeight = integer(500)
        // Keep captions visually secondary but WCAG AA readable on the #F2F2F2 background.
        color = Color("#666666")
    }
    +text
}

external interface WorkspaceFrameProps : PropsWithClassName {
    var user: User
}

val AccountFrame = FC<WorkspaceFrameProps> { props ->
    val layoutMode = use(LayoutModeContext)!!
    Stack {
        direction = FlexDirection.row
        css(props.className) {
            alignItems = AlignItems.center
            gap = 9.px
        }
        div {
            css {
                width = 36.px
                height = 36.px
                borderRadius = 50.pct
                backgroundColor = Color(stringToColor(props.user.id))
                flexShrink = number(0.0)
            }
        }
        Stack {
            css {
                flexGrow = number(1.0)
                flexShrink = number(1.0)
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                whiteSpace = WhiteSpace.nowrap
            }
            div {
                css {
                    fontFamily = sansSerif
                    fontWeight = integer(600)
                    fontSize = 15.px
                    lineHeight = 18.px
                    color = Color("#222222")
                }
                +props.user.displayName
            }
            div {
                css {
                    fontFamily = sansSerif
                    if (layoutMode == LayoutMode.PHONE) {
                        fontSize = 15.px
                        lineHeight = 18.px
                    } else {
                        fontSize = 10.px
                        lineHeight = 12.px
                    }
                    color = Color("#666666")
                }
                +(props.user.email ?: "")
            }
        }
    }
}

external interface DashboardHeaderProps : Props {
    var onAccountDialogOpen: (() -> Unit)?
}

external interface DemoPillProps : Props {
    var small: Boolean?
}

val DemoPill = FC<DemoPillProps> { props ->
    val small = props.small ?: false
    span {
        css {
            fontFamily = sansSerif
            if (small) {
                fontSize = 13.px
                lineHeight = 16.px
            } else {
                fontSize = 14.px
                lineHeight = 17.px
            }
            fontWeight = integer(600)
            borderRadius = 20.px
            backgroundColor = RoomPalette.red.color
            color = RoomPalette.red.text.color
            padding = Padding(3.px, 7.px)
            alignSelf = AlignSelf.start
        }
        +"DEMO"
    }
}

val PhoneHeader = FC<DashboardHeaderProps> { props ->
    val layoutMode = use(LayoutModeContext)!!
    header {
        css {
            display = Display.flex
            justifyContent = JustifyContent.center
            flexShrink = number(0.0)
            height = 60.px
            width = 100.pct
            top = 0.px
            position = Position.fixed
            backgroundColor = Color("#F2F2F2")
        }

        div {
            css {
                height = 60.px
                display = Display.flex
                alignItems = AlignItems.center
                padding = Padding(0.px, layoutMode.contentSidePad)
                width = layoutMode.contentWidth
            }
            useMyUser()?.let { user ->
                AccountFrame {
                    css {
                        flexGrow = number(1.0)
                        flexShrink = number(1.0)
                    }
                    this.user = user
                }
                IconButton {
                    NavMenuIcon {}
                    onClick = {props.onAccountDialogOpen?.invoke() }
                }
            }
        }
    }
}

/** Days without an update after which an own prediction is surfaced as possibly stale. */
private const val STALE_AFTER_DAYS = 14
/** Questions closing within this many days get a "closes soon" note. */
private const val CLOSING_SOON_DAYS = 7
/** How long a resolved question stays in the "Recently resolved" section. */
private const val RESOLVED_SHOWN_DAYS = 60

/** Phone-only dashboard tabs: the root page doubles as the app's main navigation
 * there, so the room list and the attention feed each get a tab. */
private enum class DashboardTab { ROOMS, OVERVIEW }

private class AttentionEntry(
    val item: DashboardItem,
    val footer: String? = null,
    val footerColor: Color? = null,
    /** Group prediction to display on the sticker (display policy pre-applied). */
    val groupPred: Prediction? = null,
)

private external interface StickerRowProps : Props {
    var items: List<AttentionEntry>
}

/** A horizontally scrollable sticker row. The scrollbar is hidden; fading gradients
 * at the edges indicate more content in that direction instead. */
private val StickerRow = FC<StickerRowProps> { props ->
    val layoutMode = use(LayoutModeContext)!!
    val scrollRef = useRef<HTMLDivElement>()
    var fadeLeft by useState(false)
    var fadeRight by useState(false)

    fun updateFades() {
        val el = scrollRef.current ?: return
        fadeLeft = el.scrollLeft > 1
        fadeRight = el.scrollLeft < el.scrollWidth - el.clientWidth - 1
    }

    useEffect(props.items) { updateFades() }

    div {
        css {
            position = Position.relative
            flexShrink = number(0.0)
        }
        div {
            ref = scrollRef
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                gap = 15.px
                padding = Padding(5.px, layoutMode.contentSidePad)
                overflowX = Auto.auto
                "&::-webkit-scrollbar" {
                    display = None.none
                }
                asDynamic().scrollbarWidth = "none"
            }
            onScroll = { updateFades() }
            props.items.forEach { e ->
                QuestionSticker {
                    key = e.item.question.id
                    this.to = e.item.url
                    this.roomName = e.item.roomName
                    this.roomPalette = e.item.roomColor.palette
                    this.questionName = e.item.questionName
                    this.footer = e.footer
                    this.footerColor = e.footerColor
                    this.groupPred = e.groupPred
                    this.stickerKey = e.item.question.id
                }
            }
        }
        // The overlays fade the row into the page background (#F2F2F2).
        if (fadeLeft) div {
            css {
                position = Position.absolute
                top = 0.px
                bottom = 0.px
                left = 0.px
                width = 56.px
                background = "linear-gradient(to right, #F2F2F2, rgba(242,242,242,0))".unsafeCast<Gradient>()
                pointerEvents = None.none
            }
        }
        if (fadeRight) div {
            css {
                position = Position.absolute
                top = 0.px
                bottom = 0.px
                right = 0.px
                width = 56.px
                background = "linear-gradient(to left, #F2F2F2, rgba(242,242,242,0))".unsafeCast<Gradient>()
                pointerEvents = None.none
            }
        }
    }
}

val Dashboard = FC<Props> {
    val rooms = useRooms()
    val layoutMode = use(LayoutModeContext)!!
    val sidebarState = use(SidebarContext)!!
    val navigate = useNavigate()
    val isAdmin = useIsAdmin()

    var dialogOpen by useState(false)
    var phoneTab by useState(DashboardTab.ROOMS)
    UserMenu {
        open = dialogOpen
        onClose = { dialogOpen = false }
    }

    if (layoutMode == LayoutMode.PHONE) {
        PhoneHeader {
            onAccountDialogOpen = { dialogOpen = true }
        }
    } else if (sidebarState.isOverlay) {
        // Tablet: the sidebar is a closed overlay, so give the dashboard the same
        // slim header with a sidebar toggle that other pages have.
        PageHeader {
            title = ""
            allowSidebar = true
        }
    }

    val myCalib = useSuspendResult {
        Client.call(Api.calibration(), CalibrationRequest())
    }
    val myScores = useSuspendResult {
        Client.call(Api.myScores())
    }

    // The section topics deliver server-computed candidate lists (see DashboardItem);
    // all "now" comparisons happen here so the backing views stay clock-free.
    val waitingRaw = (useSubscription(Topics.dashboardWaiting()) as? WSData)?.data ?: emptyList()
    val revisitRaw = (useSubscription(Topics.dashboardRevisit()) as? WSData)?.data ?: emptyList()
    val resolvedRaw = (useSubscription(Topics.dashboardResolved()) as? WSData)?.data ?: emptyList()
    val moderationRaw = (useSubscription(Topics.dashboardModeration()) as? WSData)?.data ?: emptyList()

    // Group answers on stickers, same semantics as the room grid: answered items
    // always show them (baseline), the eye switch adds scan mode — group answers
    // on questions the viewer has NOT answered. One page-global toggle rather
    // than inheriting each room's lens: what you see here is controlled here.
    val myPredictions = useMyPredictions()
    val dashServerShown = useMyUser()?.uiSettings?.get("dashboardSpoilers") == "shown"
    var dashOptimistic by useState<Boolean?>(null)
    useEffect(dashServerShown) { dashOptimistic = null }
    val dashSpoilersShown = dashOptimistic ?: dashServerShown
    val canToggleDashSpoilers = (waitingRaw + moderationRaw).any {
        it.groupPred != null && myPredictions[it.question] == null
    }
    fun shownGp(item: DashboardItem): Prediction? = item.groupPred?.takeIf {
        myPredictions[item.question] != null || dashSpoilersShown
    }

    val now = Clock.System.now()

    // Open questions the user has not answered yet, newest first. Subsumes the old
    // "recently opened" list, which ignored whether the user had already answered.
    val waiting = waitingRaw
        .take(16)
        .map { item ->
            // Ceiling in days: 2.5 days left reads as "closes in 3 days".
            val closesIn = item.closeTime?.takeIf { it > now }?.let { ((it - now).inWholeMinutes + 1439) / 1440 }
            val footer = when {
                closesIn == null || closesIn > CLOSING_SOON_DAYS -> null
                closesIn == 0L -> "closes today"
                closesIn == 1L -> "closes tomorrow"
                else -> "closes in $closesIn days"
            }
            AttentionEntry(item, footer, footer?.let { Color("#E65100") }, groupPred = shownGp(item))
        }

    // Open questions whose last prediction by the user is old enough that new
    // information may have accumulated since.
    val stale = revisitRaw
        .mapNotNull { item ->
            val ts = item.predictionTs ?: return@mapNotNull null
            val days = (now.epochSeconds - ts) / 86400
            if (days < STALE_AFTER_DAYS) return@mapNotNull null
            AttentionEntry(item, "updated $days days ago", groupPred = shownGp(item))
        }
        .take(16)

    val resolvedRecently = resolvedRaw
        .filter { it.resolvedAt != null && (now - it.resolvedAt!!).inWholeDays <= RESOLVED_SHOWN_DAYS }
        .take(12)
        .map { item ->
            val score = myScores?.items?.firstOrNull { it.questionRef.id == item.question.id }?.score
            val footer = (item.resolution ?: "") + (score?.let { " · score ${it.toFixed(1)}" } ?: "")
            AttentionEntry(item, footer, Color("#2E7D32"))
        }

    // Moderator queue: drafts not yet opened and questions past their scheduled
    // resolution, in rooms the user moderates.
    val moderation = moderationRaw
        .mapNotNull { item ->
            when (item.moderation) {
                DashboardModerationKind.DRAFT -> AttentionEntry(item, "draft — not opened")
                DashboardModerationKind.RESOLUTION_DUE -> {
                    val due = item.resolveDue ?: return@mapNotNull null
                    if (due >= now) return@mapNotNull null
                    val days = (now - due).inWholeDays
                    val footer = if (days == 0L) "resolution overdue" else "overdue by $days days"
                    AttentionEntry(item, footer, Color("#E65100"), groupPred = shownGp(item))
                }
                null -> null
            }
        }
        .take(12)

    main {
        css {
            // The phone header is fixed and needs to be compensated for.
            marginTop = if (layoutMode == LayoutMode.PHONE) 60.px else 12.px
            flexGrow = number(1.0)
            display = Display.flex
            flexDirection = FlexDirection.column
            overflow = Auto.auto
            gap = 12.px
            width = layoutMode.wideContentWidth
            overflow = Overflow.visible
            alignSelf = AlignSelf.center
            marginLeft = Auto.auto
            marginRight = Auto.auto
        }

        // Entry point to workspace-wide search. The first keystroke hands off to the search
        // page, which picks the query up from the URL and takes over the focus.
        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.row
                alignItems = AlignItems.center
                gap = 8.px
                margin = Margin(12.px, layoutMode.contentSidePad, 0.px)
                padding = Padding(0.px, 12.px)
                backgroundColor = NamedColor.white
                border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
                borderRadius = 10.px
                flexShrink = number(0.0)
            }
            div {
                css {
                    display = Display.flex
                    color = Color("#999999")
                    flexShrink = number(0.0)
                }
                ZoomIcon { size = 20 }
            }
            TextInput {
                css {
                    backgroundColor = NamedColor.transparent
                    padding = Padding(12.px, 0.px)
                    focus { outline = None.none }
                }
                placeholder = "Search questions across all rooms…"
                onChange = { ev ->
                    val typed = ev.target.value
                    if (typed.isNotEmpty()) navigate(searchUrl(typed))
                }
            }
            if (canToggleDashSpoilers) {
                span {
                    title = if (dashSpoilersShown)
                        "Showing all group answers you may see, including questions you have not answered. " +
                        "Switch off to reveal them only after you answer."
                    else
                        "Group answers show only where you have answered. " +
                        "Switch on to also see them where you have not."
                    css {
                        display = Display.flex
                        alignItems = AlignItems.center
                        flexShrink = number(0.0)
                        marginLeft = Auto.auto
                    }
                    Switch {
                        switchHeight = 26.0
                        switchWidth = 50.0
                        noColor = true
                        onIcon = EyeIcon.create()
                        offIcon = EyeOffIcon.create()
                        checked = dashSpoilersShown
                        onChange = { e ->
                            val newShown = e.target.checked
                            dashOptimistic = newShown
                            runCoroutine {
                                Client.call(Api.profileUiSetting(),
                                    SetUiSetting("dashboardSpoilers", if (newShown) "shown" else null))
                            }
                        }
                    }
                }
            }
        }

        if (layoutMode == LayoutMode.PHONE) {
            val attentionCount = waiting.size + stale.size + moderation.size
            Stack {
                direction = FlexDirection.row
                css {
                    margin = Margin(8.px, layoutMode.contentSidePad, 0.px)
                    flexShrink = number(0.0)
                }
                QuestionEstimateTabButton {
                    +"Rooms"
                    active = phoneTab == DashboardTab.ROOMS
                    onClick = { phoneTab = DashboardTab.ROOMS }
                }
                QuestionEstimateTabButton {
                    +(if (attentionCount > 0) "Overview ($attentionCount)" else "Overview")
                    active = phoneTab == DashboardTab.OVERVIEW
                    onClick = { phoneTab = DashboardTab.OVERVIEW }
                }
            }
        }

        val showCalibGraph = (myCalib != null && myCalib.entries.any { it.value.enoughData })
        val showScores = myScores != null && myScores.items.isNotEmpty()

        if (layoutMode != LayoutMode.PHONE || phoneTab == DashboardTab.OVERVIEW) {
            if (waiting.isNotEmpty()) {
                title("Waiting for your prediction", layoutMode.contentSidePad)
                StickerRow { items = waiting.take(16) }
            }
            if (stale.isNotEmpty()) {
                title("Predictions that may need updating", layoutMode.contentSidePad)
                StickerRow { items = stale.take(16) }
            }
            if (resolvedRecently.isNotEmpty()) {
                title("Recently resolved", layoutMode.contentSidePad)
                StickerRow { items = resolvedRecently }
            }
            if (moderation.isNotEmpty()) {
                title("Moderation queue", layoutMode.contentSidePad)
                StickerRow { items = moderation }
            }
            if (waiting.isEmpty() && stale.isEmpty() && resolvedRecently.isEmpty() && moderation.isEmpty()
                    && rooms.isNotEmpty()) {
                div {
                    css {
                        padding = Padding(24.px, layoutMode.contentSidePad, 0.px)
                        fontFamily = sansSerif
                        fontSize = 14.px
                        color = Color("#666666")
                    }
                    +"Nothing needs your attention right now."
                }
            }

            if (showCalibGraph || showScores) div {
                css {
                    display = Display.flex
                    flexDirection = if (layoutMode >= LayoutMode.TABLET) FlexDirection.row else FlexDirection.column
                    gap = 20.px
                    alignItems = AlignItems.stretch
                    if (layoutMode >= LayoutMode.TABLET) {
                        "& > *" {
                            flexGrow = number(1.0)
                            flexBasis = 0.px
                            minWidth = 0.px
                        }
                    }
                }

                if (showCalibGraph) Stack {
                    title("Your calibration", layoutMode.contentSidePad)
                    Link {
                        to = "/calibration"
                        css(LinkUnstyled) {
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            gap = 3.px
                            fontWeight = integer(500)
                        }

                        div {
                            css {
                                height = 150.px
                                paddingLeft = layoutMode.contentSidePad
                                paddingRight = layoutMode.contentSidePad
                                display = Display.flex
                                flexDirection = FlexDirection.column
                                alignItems = AlignItems.stretch
                                justifyContent = JustifyContent.stretch
                                border = Border(1.px, LineStyle.solid, Color("#666"))
                                borderRadius = 10.px
                                overflow = Overflow.hidden
                            }
                            CalibrationGraphContent {
                                areaLabels = false
                                calib = myCalib ?: CalibrationVector()
                            }
                        }
                        div {
                            css {
                                textAlign = TextAlign.right
                                paddingRight = layoutMode.contentSidePad
                            }
                            a {
                                css {
                                    color = MainPalette.primary.color
                                    textDecoration = TextDecoration.underline
                                    fontSize = 14.px
                                }
                                href = "/calibration"
                                +"Open calibration statistics >"
                            }
                        }
                    }
                }

                if (showScores) Stack {
                    title("Your scores", layoutMode.contentSidePad)
                    Link {
                        to = "/scores"
                        css(LinkUnstyled) {
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            gap = 3.px
                            fontWeight = integer(500)
                        }
                        div {
                            css {
                                paddingLeft = layoutMode.contentSidePad
                                paddingRight = layoutMode.contentSidePad
                                display = Display.flex
                                flexDirection = FlexDirection.row
                                alignItems = AlignItems.baseline
                                gap = 8.px
                                border = Border(1.px, LineStyle.solid, Color("#666"))
                                borderRadius = 10.px
                                padding = Padding(12.px, 16.px)
                            }
                            span {
                                css {
                                    fontWeight = integer(700)
                                    fontSize = 22.px
                                }
                                +"Total: ${myScores!!.totalScore.toFixed(1)}"
                            }
                            span {
                                css {
                                    color = Color("#666")
                                    fontSize = 14.px
                                }
                                val numRooms = myScores.items.map { it.roomRef.id }.distinct().size
                                +"${myScores.items.size} question${if (myScores.items.size != 1) "s" else ""}"
                                +" in $numRooms room${if (numRooms != 1) "s" else ""}"
                            }
                        }
                        div {
                            css {
                                textAlign = TextAlign.right
                                paddingRight = layoutMode.contentSidePad
                            }
                            a {
                                css {
                                    color = MainPalette.primary.color
                                    textDecoration = TextDecoration.underline
                                    fontSize = 14.px
                                }
                                href = "/scores"
                                +"Open scoring dashboard >"
                            }
                        }
                    }
                }
            }

        }

        // With a sidebar present these sections would only duplicate it; they are
        // needed on phone, which has no sidebar (in its Rooms tab).
        if (!sidebarState.isAvailable && phoneTab == DashboardTab.ROOMS) {
            if ((!showCalibGraph && !showScores) || isAdmin) {
                title("Workspace", layoutMode.contentSidePad)
                SidebarActions { hideCalib = showCalibGraph }
            }

            title("Rooms", layoutMode.contentSidePad)

            RoomList {
                canCreate = useMyUser()?.type?.isProper() ?: false
            }
        }
    }
}

external interface UserMenuProps : Props {
    var open: Boolean
    var onClose: (() -> Unit)?

    /**
     * Skip items that duplicate navigation already visible next to the menu trigger.
     * Used when the menu is opened from the sidebar, which lists Calibration, Your
     * scores and Users itself.
     */
    var hideSidebarDuplicates: Boolean?
}

val UserMenu = FC<UserMenuProps> { props ->
    val loginState = use(LoginContext)!!
    val navigate = useNavigate()
    val hideDuplicates = props.hideSidebarDuplicates ?: false

    DialogMenu {
        open = props.open
        onClose = { props.onClose?.invoke() }

        DialogMenuNav {
            text = "User settings"
            icon = SettingsIcon
            this.navigate = "/profile"
        }
        if (!hideDuplicates) {
            DialogMenuNav {
                text = "Calibration"
                this.navigate = "/calibration"
            }
            DialogMenuNav {
                text = "Your scores"
                this.navigate = "/scores"
            }
        }

        DialogMenuItem {
            text = "Log out"
            icon = LogoutIcon
            variant = DialogMenuItemVariant.dangerous
            onClick = {
                runCoroutine {
                    Client.call(Api.logout())
                    navigate("/")
                    loginState.logout()
                }
            }
        }
        DialogMenuSeparator {}
        DialogMenuCommonActions {
            pageName = "Dashboard"
            onClose = props.onClose
        }
        if (useIsAdmin() && !hideDuplicates) {
            DialogMenuSeparator{}
            DialogMenuHeader { this.text = "Administration" }
            DialogMenuNav {
                text = "User management"
                this.navigate = "/admin/users"
            }
        }
    }
}
