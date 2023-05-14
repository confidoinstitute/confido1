@file:JsModule("react-show-more-text")
@file:JsNonModule
package ext.showmoretext

import csstype.ClassName
import react.*

external interface ShowMoreTextProps : PropsWithChildren {
    var lines: Int?
    var more: ReactNode?
    var less: ReactNode?
    var anchorClass: ClassName?
    /** Arguments: `expanded`, `event`.*/
    var onClick: ((Boolean, Any) -> Unit)?
    var expanded: Boolean?
    var expandByClick: Boolean
    var width: Int?
    var keepNewLines: Boolean?
    var truncatedEndingComponent: String?
    var onTruncate: (() -> Unit)?
}

@JsName("default")
external val ShowMoreText: ComponentType<ShowMoreTextProps>
