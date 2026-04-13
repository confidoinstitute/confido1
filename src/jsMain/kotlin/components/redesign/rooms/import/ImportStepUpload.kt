package components.redesign.rooms.import

import Client
import components.redesign.basic.*
import components.redesign.forms.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import payloads.requests.CsvParseResponse
import payloads.requests.ImportUrlRequest
import react.*
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.p
import rooms.Room
import web.cssom.*
import emotion.react.css
import hooks.useCoroutineLock
import kotlinx.coroutines.await
import web.html.InputType
import kotlin.js.Promise

external interface ImportStepUploadProps : Props {
    var room: Room
    var onParsed: (CsvParseResponse) -> Unit
}

/** Read a web File as a ByteArray via FileReader + ArrayBuffer. */
private fun readFileAsBytes(file: dynamic): Promise<ByteArray> = Promise { resolve, reject ->
    val reader = js("new FileReader()")
    reader.onload = {
        val arrayBuffer = reader.result
        val uint8 = js("new Uint8Array(arrayBuffer)")
        val len: Int = uint8.length as Int
        val bytes = ByteArray(len) { i: Int -> (uint8[i] as Number).toByte() }
        resolve(bytes)
    }
    reader.onerror = {
        reject(Exception("Failed to read file"))
    }
    reader.readAsArrayBuffer(file)
}

/** Check whether a string looks like a Google Sheets URL. */
private fun isGoogleSheetsUrl(url: String): Boolean =
    url.contains("docs.google.com/spreadsheets/")

val ImportStepUpload = FC<ImportStepUploadProps> { props ->
    var error by useState<String?>(null)
    var uploading by useState(false)
    var fileName by useState<String?>(null)
    var sheetsUrl by useState("")
    var fetchingUrl by useState(false)
    val submitLock = useCoroutineLock()

    LayoutWidthWrapper {
        css {
            gap = 30.px
            padding = 20.px
            alignItems = AlignItems.center
        }

        p {
            css {
                fontFamily = sansSerif
                fontSize = 14.px
                lineHeight = 20.px
                color = Color("#666666")
                margin = 0.px
            }
            +"Import questions from a CSV/TSV file or a Google Sheets spreadsheet. "
            +"The data should have at least a column with question titles."
        }

        FormSection {
            title = "Upload CSV file"
            Stack {
                css {
                    gap = 15.px
                    alignItems = AlignItems.stretch
                }

                div {
                    css {
                        border = Border(2.px, LineStyle.dashed, Color("#CCCCCC"))
                        borderRadius = 8.px
                        padding = 30.px
                        textAlign = TextAlign.center
                        backgroundColor = Color("#FAFAFA")
                        cursor = Cursor.pointer
                        "&:hover" {
                            borderColor = Color("#6319FF")
                            backgroundColor = Color("#F5F0FF")
                        }
                    }

                    input {
                        asDynamic().type = "file"
                        accept = ".csv,.tsv,.txt"
                        css {
                            width = 100.pct
                            cursor = Cursor.pointer
                            fontFamily = sansSerif
                            fontSize = 15.px
                        }
                        disabled = uploading || fetchingUrl
                        onChange = onChange@{ event ->
                            val target = event.target.asDynamic()
                            val files = target.files ?: return@onChange
                            val selectedFile = files[0] ?: return@onChange
                            val selectedFileName: String = selectedFile.name as String
                            fileName = selectedFileName
                            error = null

                            submitLock {
                                uploading = true
                                try {
                                    val byteArray = readFileAsBytes(selectedFile).await()

                                    val resp = Client.httpClient.submitFormWithBinaryData(
                                        url = "${props.room.urlPrefix}/questions/import/parse",
                                        formData = formData {
                                            append("file", byteArray, Headers.build {
                                                append(HttpHeaders.ContentDisposition, "filename=\"$selectedFileName\"")
                                                append(HttpHeaders.ContentType, "application/octet-stream")
                                            })
                                        }
                                    )

                                    if (resp.status.isSuccess()) {
                                        val parseResponse: CsvParseResponse = resp.body()
                                        props.onParsed(parseResponse)
                                    } else {
                                        val errorMsg: String = resp.body()
                                        error = errorMsg
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Upload failed"
                                } finally {
                                    uploading = false
                                }
                            }
                        }
                    }
                }

                if (uploading) {
                    div {
                        css {
                            fontFamily = sansSerif
                            fontSize = 14.px
                            color = Color("#6319FF")
                            textAlign = TextAlign.center
                        }
                        +"Uploading and parsing${fileName?.let { " $it" } ?: ""}..."
                    }
                }
            }
        }

        // Divider
        div {
            css {
                width = 100.pct
                display = Display.flex
                alignItems = AlignItems.center
                gap = 12.px
                fontFamily = sansSerif
                fontSize = 13.px
                color = Color("#999999")
            }
            div {
                css {
                    flex = Flex(number(1.0), number(1.0), Auto.auto)
                    height = 1.px
                    backgroundColor = Color("#DDDDDD")
                }
            }
            +"or"
            div {
                css {
                    flex = Flex(number(1.0), number(1.0), Auto.auto)
                    height = 1.px
                    backgroundColor = Color("#DDDDDD")
                }
            }
        }

        FormSection {
            title = "Import from Google Sheets"
            Stack {
                css {
                    gap = 15.px
                    alignItems = AlignItems.stretch
                }
                p {
                    css {
                        fontFamily = sansSerif
                        fontSize = 14.px
                        lineHeight = 20.px
                        color = Color("#666666")
                        margin = 0.px
                    }
                    +"Paste a Google Sheets sharing link. The spreadsheet must be publicly accessible "
                    +"(\"Anyone with the link\" in sharing settings)."
                }

                Stack {
                    direction = FlexDirection.row
                    css {
                        gap = 10.px
                        alignItems = AlignItems.stretch
                    }
                    TextInput {
                        css {
                            flex = Flex(number(1.0), number(1.0), Auto.auto)
                        }
                        placeholder = "https://docs.google.com/spreadsheets/d/..."
                        value = sheetsUrl
                        disabled = uploading || fetchingUrl
                        onChange = { event ->
                            sheetsUrl = event.target.value
                            error = null
                        }
                    }
                    Button {
                        disabled = sheetsUrl.isBlank() || !isGoogleSheetsUrl(sheetsUrl) || uploading || fetchingUrl
                        onClick = {
                            error = null
                            submitLock {
                                fetchingUrl = true
                                try {
                                    val resp = Client.sendDataRequest(
                                        "${props.room.urlPrefix}/questions/import/parse-url",
                                        ImportUrlRequest(url = sheetsUrl),
                                    )

                                    if (resp.status.isSuccess()) {
                                        val parseResponse: CsvParseResponse = resp.body()
                                        props.onParsed(parseResponse)
                                    } else {
                                        val errorMsg: String = resp.body()
                                        error = errorMsg
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to fetch spreadsheet"
                                } finally {
                                    fetchingUrl = false
                                }
                            }
                        }
                        +"Fetch"
                    }
                }

                if (fetchingUrl) {
                    div {
                        css {
                            fontFamily = sansSerif
                            fontSize = 14.px
                            color = Color("#6319FF")
                            textAlign = TextAlign.center
                        }
                        +"Fetching and parsing spreadsheet..."
                    }
                }
            }
        }

        // Shared error display
        error?.let { err ->
            div {
                css {
                    fontFamily = sansSerif
                    fontSize = 14.px
                    color = Color("#F35454")
                    padding = 10.px
                    backgroundColor = Color("#FFF0F0")
                    borderRadius = 5.px
                }
                +err
            }
        }
    }
}
