package tools.confido.web.components.rooms

import tools.confido.web.components.HelpIcon
import tools.confido.web.components.CheckmarkIcon
import tools.confido.web.components.useMyUser
import tools.confido.web.components.useDisconnected
import tools.confido.web.components.basic.*
import tools.confido.web.components.rooms.dialog.RoleHelpDialog
import web.cssom.*
import web.dom.document
import web.animations.requestAnimationFrame
import web.html.HTMLElement
import web.html.ButtonType
import web.html.button
import emotion.react.css
import js.objects.unsafeJso as jso
import react.*
import react.dom.aria.AriaHasPopup
import react.dom.aria.AriaRole
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import tools.confido.rooms.RoomRole
import tools.confido.state.appConfig

external interface RoleSelectorProps : PropsWithClassName {
    var value: RoomRole
    var onChange: ((RoomRole) -> Unit)?
    /** Offer only the roles guests can hold (adding members, invitation links). */
    var guestRolesOnly: Boolean?
    /** The role is a guest's: roles not available to guests are offered disabled. */
    var forGuest: Boolean?
    /** Read-only: the menu still opens, to explain the roles, but nothing can be picked. */
    var disabled: Boolean?
}

private val RoleSelectorTrigger = button.withStyle {
    all = Globals.unset
    boxSizing = BoxSizing.borderBox
    border = Border(1.px, LineStyle.solid, Color("#DDDDDD"))
    borderRadius = 5.px
    cursor = Cursor.pointer
    textAlign = TextAlign.left

    fontFamily = sansSerif
    fontSize = 17.px
    lineHeight = 18.px
    color = Color("#555555")

    padding = Padding(10.px, 8.px)
    paddingLeft = 20.px

    // Same caret as the native-select based forms.Select.
    backgroundColor = Color("#FFFFFF")
    backgroundImage = url("\"data:image/svg+xml;utf8,<svg height='5' width='10' xmlns='http://www.w3.org/2000/svg'><path fill='%23dddddd' d='M0,0H10L5,5Z'/></svg>\"")
    backgroundRepeat = BackgroundRepeat.noRepeat
    backgroundPositionX = 6.px
    backgroundPositionY = 18.px

    focusVisible {
        outline = Outline(2.px, LineStyle.solid)
        outlineColor = Color("#6319FF")
    }
}

private external interface RoleOptionProps : Props {
    var role: RoomRole
    var selected: Boolean
    /** Why this role cannot be picked; null when it can. */
    var unavailableReason: String?
    /** The whole selector is read-only: options are informational, not dimmed. */
    var readOnly: Boolean
    var onPick: (() -> Unit)?
}

private val RoleOption = FC<RoleOptionProps> { props ->
    val pickable = !props.readOnly && props.unavailableReason == null
    div {
        role = AriaRole.option
        tabIndex = -1
        ariaSelected = props.selected
        ariaDisabled = !pickable
        asDynamic()["data-role-id"] = props.role.id
        css {
            display = Display.flex
            gap = 10.px
            padding = Padding(8.px, 15.px)
            outline = None.none
            cursor = if (pickable) Cursor.pointer else Cursor.default
            userSelect = None.none
            if (pickable) hover { backgroundColor = rgb(0, 0, 0, 0.05) }
            focus { backgroundColor = rgb(0, 0, 0, 0.07) }
        }
        onClick = { if (pickable) props.onPick?.invoke() }

        div {
            css {
                flex = None.none
                width = 16.px
                paddingTop = 4.px
                color = Color("#6319FF")
            }
            if (props.selected) CheckmarkIcon {}
        }
        div {
            css {
                fontFamily = sansSerif
                if (props.unavailableReason != null && !props.readOnly) opacity = number(0.5)
            }
            div {
                css { fontSize = 17.px; lineHeight = 20.px; fontWeight = integer(600) }
                +props.role.name
            }
            if (props.role.description.isNotBlank()) div {
                css { fontSize = 13.px; lineHeight = 16.px; color = Color("#666666"); marginTop = 2.px }
                +props.role.description
            }
            if (!props.readOnly) props.unavailableReason?.let { reason ->
                div {
                    css { fontSize = 13.px; lineHeight = 16.px; fontStyle = FontStyle.italic; marginTop = 2.px }
                    +reason
                }
            }
        }
    }
}

/**
 * Room role picker: a select-like button opening a [DialogMenu] (popover on desktop,
 * sheet on phone) whose options carry each role's description, with an
 * "About roles…" entry opening [RoleHelpDialog] for the overview.
 *
 * Offers only roles the viewer may grant ([canChangeRole], mirroring the server);
 * others show disabled with the reason. When the current role itself is beyond the
 * viewer's reach (or [RoleSelectorProps.disabled]), the selector is read-only.
 *
 * Keyboard: the options form a listbox — arrows/Home/End move, Enter/Space pick,
 * letters jump by name, Escape closes (desktop) and focus returns to the trigger.
 */
val RoleSelector = FC<RoleSelectorProps> { props ->
    val room = use(RoomContext)!!
    val myUser = useMyUser()
    val disconnected = useDisconnected()
    var menuOpen by useState(false)
    var helpOpen by useDialog(RoleHelpDialog, jso { guestRolesOnly = props.guestRolesOnly })
    val triggerRef = useRef<HTMLElement>(null)
    val listRef = useRef<HTMLElement>(null)

    val readOnly = props.disabled == true || disconnected || !canChangeRole(myUser, room, props.value)
    val roles = appConfig.roles.filter { props.guestRolesOnly != true || it.availableToGuests }
    fun unavailableReason(role: RoomRole) = when {
        props.forGuest == true && !role.availableToGuests -> "Not available to guests."
        !canChangeRole(myUser, room, role) -> "Grants permissions you do not have."
        else -> null
    }

    fun options() = listRef.current?.querySelectorAll("[role=option]")
        ?.let { nl -> List(nl.length) { nl.item(it) as HTMLElement } } ?: emptyList()

    fun close() {
        menuOpen = false
        triggerRef.current?.focus()
    }
    fun pick(role: RoomRole) {
        close()
        if (role.id != props.value.id) props.onChange?.invoke(role)
    }

    // Focus the current role on opening. The menu content mounts in this commit,
    // but the phone sheet slides in — defer a frame so focus does not scroll it.
    useEffect(menuOpen) {
        if (!menuOpen) return@useEffect
        requestAnimationFrame {
            val opts = options()
            (opts.firstOrNull { it.getAttribute("aria-selected") == "true" } ?: opts.firstOrNull())?.focus()
        }
    }

    RoleSelectorTrigger {
        ref = triggerRef.unsafeCast<Ref<web.html.HTMLButtonElement>>()
        type = ButtonType.button
        css(props.className) {
            if (readOnly) color = Color("#AAAAAA")
        }
        asDynamic()["data-readonly"] = readOnly.toString()
        ariaHasPopup = AriaHasPopup.listbox
        ariaExpanded = menuOpen
        onClick = { menuOpen = true }
        onKeyDown = { e ->
            if (e.key == "ArrowDown" || e.key == "ArrowUp") {
                e.preventDefault()
                menuOpen = true
            }
        }
        +props.value.name
    }

    DialogMenu {
        open = menuOpen
        onClose = { close() }
        hasCloseButton = false
        div {
            ref = listRef.unsafeCast<Ref<web.html.HTMLDivElement>>()
            role = AriaRole.listbox
            ariaLabel = "Role"
            onKeyDown = { e ->
                val opts = options()
                val cur = opts.indexOf(document.activeElement)
                fun move(i: Int) { opts.getOrNull(i.coerceIn(0, opts.lastIndex))?.focus() }
                when {
                    e.key == "ArrowDown" -> { e.preventDefault(); move(cur + 1) }
                    e.key == "ArrowUp" -> { e.preventDefault(); move(cur - 1) }
                    e.key == "Home" -> { e.preventDefault(); move(0) }
                    e.key == "End" -> { e.preventDefault(); move(opts.lastIndex) }
                    e.key == "Enter" || e.key == " " -> {
                        e.preventDefault()
                        opts.getOrNull(cur)?.takeIf { it.getAttribute("aria-disabled") != "true" }
                            ?.let { el -> roles.find { it.id == el.getAttribute("data-role-id") } }
                            ?.let { pick(it) }
                    }
                    e.key.length == 1 -> {
                        // Type-ahead: next role (after the focused one) starting with the letter.
                        val n = roles.size
                        (1..n).map { (cur + it).mod(n) }
                            .firstOrNull { roles[it].name.startsWith(e.key, ignoreCase = true) }
                            ?.let { move(it) }
                    }
                }
            }
            roles.map { role ->
                RoleOption {
                    key = role.id
                    this.role = role
                    selected = role.id == props.value.id
                    unavailableReason = unavailableReason(role)
                    this.readOnly = readOnly
                    onPick = { pick(role) }
                }
            }
        }
        DialogMenuSeparator {}
        DialogMenuItem {
            text = "About roles…"
            icon = HelpIcon
            onClick = { helpOpen = true }
        }
    }
}
