package com.amz.nftlistener.domain

object PayloadJson {
    fun encode(fields: NotificationFields): String {
        return buildString {
            append('{')
            appendString("eventId", fields.eventId)
            append(',')
            appendLong("postedAt", fields.postedAt)
            append(',')
            appendString("packageName", fields.packageName)
            append(',')
            appendString("appLabel", fields.appLabel)
            append(',')
            appendString("title", fields.title)
            append(',')
            appendString("text", fields.text)
            append(',')
            appendString("subText", fields.subText)
            append(',')
            appendString("channelId", fields.channelId)
            append(',')
            appendBoolean("isOngoing", fields.isOngoing)
            append('}')
        }
    }

    private fun StringBuilder.appendString(key: String, value: String) {
        append('"').append(key).append('"').append(':')
        append('"').append(escape(value)).append('"')
    }

    private fun StringBuilder.appendLong(key: String, value: Long) {
        append('"').append(key).append('"').append(':').append(value)
    }

    private fun StringBuilder.appendBoolean(key: String, value: Boolean) {
        append('"').append(key).append('"').append(':').append(value)
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (ch < '\u0020') {
                        out.append("\\u")
                        out.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        return out.toString()
    }
}
