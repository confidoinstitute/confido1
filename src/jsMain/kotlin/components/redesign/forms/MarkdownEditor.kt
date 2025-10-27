package components.redesign.forms

import csstype.*
import emotion.react.*
import ext.milkdown.*
import react.*
import react.dom.html.ReactHTML.div
import kotlinx.browser.document
import web.cssom.Auto
import web.cssom.Border
import web.cssom.Color
import web.cssom.Length
import web.cssom.LineStyle
import web.cssom.None
import web.cssom.Overflow
import web.cssom.Padding
import web.cssom.PointerEvents
import web.cssom.Position
import web.cssom.number
import web.cssom.pct
import web.cssom.px
import js.objects.unsafeJso as jso

// Import Milkdown CSS styles
external fun require(module: String): dynamic

val MarkdownEditorInner = FC<MarkdownEditorProps> { props ->
    div {
        css {
            ".ProseMirror" { padding = 0.px; paddingLeft=64.px }
            ".ProseMirror p" { margin = 0.px }
        }
        useEditor({ root ->
                    val crepe = Crepe(jso { this.root = root; this.defaultValue = props.value } )
                    crepe.on { mgr->
                        mgr.markdownUpdated { _, markdown, _ ->
                            props.onChange?.invoke(markdown)
                        }
                    }
                    crepe
                }, arrayOf())
        Milkdown {}
    }
}

val MarkdownEditor = FC<MarkdownEditorProps> { props->
    MilkdownProvider {
        MarkdownEditorInner{+props}
    }
}

external interface MarkdownEditorProps : PropsWithClassName {
    var value: String?
    var onChange: ((String) -> Unit)?
    var placeholder: String?
    var minHeight: Length?
    var maxHeight: Length?
    var readOnly: Boolean?
}
