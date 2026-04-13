package components.redesign.forms

import components.redesign.basic.MainPalette
import components.redesign.basic.sansSerif
import hooks.useTheme
import js.objects.unsafeJso as jso
import js.reflect.unsafeCast
import react.FC
import react.Props
import react.ReactNode
import react.select.*
import web.cssom.*

/**
 * A simple option type for react-select with a string label and value.
 */
external interface TagOption {
    var label: String
    var value: String
}

fun tagOption(label: String, value: String): TagOption = jso {
    this.label = label
    this.value = value
}

external interface TagInputProps : Props {
    /** Currently selected tags. */
    var value: Set<String>?

    /** Called when the set of selected tags changes. */
    var onChange: ((Set<String>) -> Unit)?

    /** Available tag suggestions for autocomplete. */
    var suggestions: List<String>?

    /** Placeholder text when no tags are selected. */
    var placeholder: String?
}

/**
 * Multi-chip tag input with autocomplete, built on react-select's CreatableSelect.
 *
 * Supports:
 * - Selecting from existing tag suggestions (autocomplete dropdown)
 * - Creating new tags by typing and pressing Enter
 * - Removing tags by clicking the X on each chip
 * - Keyboard navigation (backspace to remove last tag, etc.)
 */
val TagInput = FC<TagInputProps> { props ->
    val theme = useTheme()
    val primaryColor = MainPalette.primary.color.toString()
    val inputBg = theme.colors.form.inputBackground.toString()

    val currentTags = props.value ?: emptySet()

    // Convert current tags to react-select Option objects
    val selectedOptions = currentTags.map { tag -> tagOption(tag, tag) }.toTypedArray()

    // Convert suggestions to react-select Option objects (excluding already selected)
    val optionsList = (props.suggestions ?: emptyList())
        .filter { it !in currentTags }
        .map { tag -> tagOption(tag, tag) }
        .toTypedArray()

    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
    (CreatableSelect.unsafeCast<react.ComponentType<CreatableSelectProps<TagOption, GroupBase<TagOption>>>>()).invoke {
        isMulti = true
        isClearable = false
        value = Value(*selectedOptions)
        options = OptionsOrGroups<TagOption, GroupBase<TagOption>>(optionsList)
        placeholder = ReactNode(props.placeholder ?: "Add tags...")
        onChange = { newValue, _ ->
            val tags = newValue?.asArray()?.map { it.value }?.toSet() ?: emptySet()
            props.onChange?.invoke(tags)
        }
        formatCreateLabel = { input -> ReactNode("Create tag \"$input\"") }

        // Style overrides to match the project's visual language
        val stylesObj: dynamic = js("{}")
        stylesObj.control = Styles<ControlProps<TagOption, GroupBase<TagOption>>> { _ ->
            backgroundColor = inputBg.unsafeCast<BackgroundColor>()
            borderWidth = 0.px
            borderRadius = 5.px
            fontFamily = sansSerif
            fontSize = 17.px
            lineHeight = 20.px
            minHeight = 42.px
            boxShadow = None.none
            cursor = Cursor.text
        }
        stylesObj.multiValue = Styles<MultiValueProps<TagOption, GroupBase<TagOption>>> { _ ->
            backgroundColor = primaryColor.unsafeCast<BackgroundColor>()
            borderRadius = 20.px
            padding = Padding(0.px, 2.px)
        }
        stylesObj.multiValueLabel = Styles<MultiValueProps<TagOption, GroupBase<TagOption>>> { _ ->
            color = NamedColor.white.unsafeCast<Color>()
            fontFamily = sansSerif
            fontWeight = integer(600)
            fontSize = 12.px
            lineHeight = 15.px
            padding = Padding(3.px, 6.px)
            paddingLeft = 8.px
        }
        stylesObj.multiValueRemove = Styles<MultiValueProps<TagOption, GroupBase<TagOption>>> { _ ->
            color = NamedColor.white.unsafeCast<Color>()
            borderRadius = 20.px
            cursor = Cursor.pointer
            hover {
                backgroundColor = "rgba(255,255,255,0.2)".unsafeCast<BackgroundColor>()
                color = NamedColor.white.unsafeCast<Color>()
            }
        }
        stylesObj.menu = Styles<MenuProps<TagOption, GroupBase<TagOption>>> { _ ->
            borderRadius = 5.px
            overflow = Overflow.hidden
            fontFamily = sansSerif
            fontSize = 15.px
            zIndex = integer(10)
        }
        stylesObj.option = Styles<OptionProps<TagOption, GroupBase<TagOption>>> { _ ->
            fontFamily = sansSerif
            fontSize = 15.px
            cursor = Cursor.pointer
        }
        stylesObj.placeholder = Styles<PlaceholderProps<TagOption, GroupBase<TagOption>>> { _ ->
            color = "#BBBBBB".unsafeCast<Color>()
            fontFamily = sansSerif
            fontSize = 17.px
        }
        stylesObj.input = Styles<InputSpecificProps<TagOption, GroupBase<TagOption>>> { _ ->
            fontFamily = sansSerif
            fontSize = 17.px
            color = "#333333".unsafeCast<Color>()
        }
        stylesObj.dropdownIndicator = Styles<DropdownIndicatorProps<TagOption, GroupBase<TagOption>>> { _ ->
            display = None.none
        }
        stylesObj.indicatorSeparator = Styles<IndicatorSeparatorProps<TagOption, GroupBase<TagOption>>> { _ ->
            display = None.none
        }
        styles = stylesObj.unsafeCast<StylesConfig<TagOption, GroupBase<TagOption>>>()
    }
}
