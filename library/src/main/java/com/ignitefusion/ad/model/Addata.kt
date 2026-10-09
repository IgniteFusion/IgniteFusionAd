package com.ignitefusion.ad.model

import androidx.annotation.Keep

@Keep
data class Addata(
    var id: String? = null,
    var appname: String? = null,
    var content: String? = null,
    var applogo: FileRef? = null,
    var promotional: FileRef? = null,
    var video: FileRef? = null,
    var themeColor: String? = null,
    var applicationId: String? = null,
    var version: String? = null,
    var isDownload: Boolean? = null,
    var url: String? = null,
    var weight: Int = 0
) {
    @Keep
    data class FileRef(var url: String? = null)
}
