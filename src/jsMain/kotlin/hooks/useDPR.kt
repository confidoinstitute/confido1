package hooks

import browser.window
import dom.html.Window
import react.useEffect
import react.useState


val Window.devicePixelRatio get() = (window.asDynamic().devicePixelRatio ?: 1.0) as Double

// loosely based on https://github.com/rexxars/use-device-pixel-ratio
fun useDPR(): Double {
    var curDPR by useState(window.devicePixelRatio)

    useEffect(curDPR) {
        val matchMedia = window.asDynamic().matchMedia
        if (!matchMedia == null) {
            return@useEffect
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
            return@useEffect
        }

        // Safari 13.1 does not have `addEventListener`, but does have `addListener`
        if (mediaMatcher.addEventListener != null) {
            mediaMatcher.addEventListener("change", ::updateDPR)
        } else {
            mediaMatcher.addListener(::updateDPR)
        }

        cleanup {
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