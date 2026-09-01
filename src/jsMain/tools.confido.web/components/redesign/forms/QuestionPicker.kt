package tools.confido.web.components.redesign.forms

import tools.confido.web.components.redesign.basic.sansSerif
import tools.confido.web.components.redesign.basic.useAboveModalsZIndex
import js.objects.unsafeJso as jso
import react.*
import react.select.*
import react.select.Select as ReactSelectComponent
import tools.confido.question.Question
import tools.confido.state.CURRENT_QUESTION
import web.cssom.*

/**
 * react-select option type for question picker.
 */
external interface QuestionOption {
    var label: String
    var value: String
}

fun questionOption(q: Question): QuestionOption = jso {
    label = q.name
    value = q.id
}

fun questionOption(label: String, value: String): QuestionOption = jso {
    this.label = label
    this.value = value
}

/**
 * A searchable dropdown for selecting a question, built on react-select.
 *
 * Usage:
 * ```
 * QuestionPicker {
 *     questions = listOfQuestions
 *     selectedId = currentlySelectedQuestionId
 *     onChange = { newId -> ... }
 * }
 * ```
 */
external interface QuestionPickerProps : Props {
    /** Available questions to choose from. */
    var questions: List<Question>
    /** ID of the currently selected question (empty string = none selected). */
    var selectedId: String
    /** Called when the selection changes. */
    var onChange: ((String) -> Unit)?
    /** Placeholder text. */
    var placeholder: String?
    /** Whether selection can be cleared. */
    var clearable: Boolean?
    /** Offer the loop's "current question" sentinel (flow editors inside a repeat body). */
    var currentQuestionOption: Boolean?
}

private const val CURRENT_LABEL = "↻ Current question"

val QuestionPicker = FC<QuestionPickerProps> { props ->
    val currentOpt = if (props.currentQuestionOption == true)
        arrayOf(questionOption(CURRENT_LABEL, CURRENT_QUESTION.id)) else emptyArray()
    val questionOptions = currentOpt + props.questions.map { questionOption(it) }.toTypedArray()
    val selectedOption = when {
        props.selectedId == CURRENT_QUESTION.id -> arrayOf(questionOption(CURRENT_LABEL, CURRENT_QUESTION.id))
        else -> props.questions.find { it.id == props.selectedId }?.let { arrayOf(questionOption(it)) } ?: emptyArray()
    }
    // The suggestions menu escapes any clipping/scrolling ancestor (dialogs!)
    // via a body-level portal layered above the current modal stack.
    val menuZIndex = useAboveModalsZIndex()

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    (ReactSelectComponent.unsafeCast<react.ComponentType<SelectProps<QuestionOption, GroupBase<QuestionOption>>>>()).invoke {
        value = if (selectedOption.isNotEmpty()) Value(*selectedOption) else undefined
        options = OptionsOrGroups<QuestionOption, GroupBase<QuestionOption>>(questionOptions)
        placeholder = ReactNode(props.placeholder ?: "Select a question...")
        isClearable = props.clearable ?: false
        onChange = { newValue, _ ->
            val opt = newValue.unsafeCast<QuestionOption?>()
            props.onChange?.invoke(opt?.value ?: "")
        }
        menuPortalTarget = web.dom.document.body
        // Fixed positioning: the default absolute mode places the portal in
        // DOCUMENT coordinates, which lands scroll-offset pixels below the
        // control when the picker sits in a fixed dialog over a scrolled page.
        asDynamic().menuPosition = "fixed"

        val stylesObj: dynamic = js("{}")
        stylesObj.menuPortal = Styles<MenuProps<QuestionOption, GroupBase<QuestionOption>>> { _ ->
            zIndex = integer(menuZIndex)
        }
        stylesObj.control = Styles<ControlProps<QuestionOption, GroupBase<QuestionOption>>> { _ ->
            fontFamily = sansSerif
            fontSize = 14.px
            minHeight = 36.px
        }
        stylesObj.menu = Styles<MenuProps<QuestionOption, GroupBase<QuestionOption>>> { _ ->
            fontFamily = sansSerif
            fontSize = 14.px
            zIndex = integer(100)
        }
        stylesObj.option = Styles<OptionProps<QuestionOption, GroupBase<QuestionOption>>> { _ ->
            fontFamily = sansSerif
            fontSize = 14.px
            cursor = Cursor.pointer
        }
        styles = stylesObj.unsafeCast<StylesConfig<QuestionOption, GroupBase<QuestionOption>>>()
    }
}
