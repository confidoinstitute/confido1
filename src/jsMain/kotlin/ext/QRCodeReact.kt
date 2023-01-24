@file:JsModule("qrcode.react")
@file:JsNonModule
package ext.qrcodereact

import react.FC
import react.Props
import react.PropsWithClassName

external interface QRCodeProps : PropsWithClassName {
    var value: String
    var includeMargin: Boolean
    var size: Int
    var level: String
}

external val QRCodeSVG: FC<QRCodeProps>
external val QRCodeCanvas: FC<QRCodeProps>


