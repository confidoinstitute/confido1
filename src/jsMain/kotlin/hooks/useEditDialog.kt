package hooks

import react.*
import tools.confido.utils.randomString

external interface EditEntityDialogProps<E> : Props {
    var entity: E?
    var open: Boolean
    var onClose: (() -> Unit)?
}

/** H(ook|ack) to support components that show a list of entities and allow
 *  editing them using a dialog. This hook manages the dialog and ensures
 *  its inner state is reset on each edit by changing its React key.
 *  For example usage, see QuestionList.
 */
inline fun <E, DP: EditEntityDialogProps<E>> ChildrenBuilder.useEditDialog(comp: FC<DP>, props: DP? = null, temporaryHide: Boolean = false): ((E?)->Unit) {
    var editedEntity by useState<E?>(null)
    var editedKey by useState("")
    var editOpen by useState(false)
    useLayoutEffect(editOpen) {
        if (editOpen)
            editedKey = randomString(20)
    }
    val uedKey = useId() // We may use multiple useEditDialogs in one place (e.g. in user admin edit dialog + delete dialog)

    comp {
        key = "##editDialog##$uedKey##$editedKey"
        entity = editedEntity
        open = editOpen && !temporaryHide
        onClose = { editOpen = false }
        +props
    }

    return {  editedEntity = it; editOpen = true }
}
