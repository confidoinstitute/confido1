package hooks

import web.window.window
import web.window.Window
import react.useEffect
import react.useEffectWithCleanup
import react.useState


val Window.devicePixelRatio get() = (window.asDynamic().devicePixelRatio ?: 1.0) as Double

// loosely based on https://github.com/rexxars/use-device-pixel-ratio
fun useDPR(): Double {
    var curDPR by useState(window.devicePixelRatio)

    useEffectWithCleanup(curDPR) {
        val matchMedia = window.asDynamic().matchMedia
        if (!matchMedia == null) {
            return@useEffectWithCleanup
        }

        // add some tolerance as DPR is a floating point value, and it might not match
        // exactly when stringified
        val mediaMatcher = matchMedia("screen and (min-resolution: ${curDPR-0.001}dppx) and (max-resolution: ${curDPR+0.001}dppx)")
        fun updateDPR() {
            curDPR = window.devicePixelRatio
            console.log("Updating DPR: ${window.devicePixelRatio}")
        }

        if (!mediaMatcher.matches) {
            updateDPR()
            return@useEffectWithCleanup
        }

        // Safari 13.1 does not have `addEventListener`, but does have `addListener`
        if (mediaMatcher.addEventListener != null) {
            mediaMatcher.addEventListener("change", ::updateDPR)
        } else {
            mediaMatcher.addListener(::updateDPR)
        }

        onCleanup {
            if (mediaMatcher.removeEventListener != null) {
                mediaMatcher.removeEventListener("change", ::updateDPR)
            } else {
                mediaMatcher.removeListener(::updateDPR)
            }
        }


        if (!mediaMatcher.matches) { // prevent race conditions
            updateDPR()
        }
    }

    return curDPR
}