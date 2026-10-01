package com.tff.app.core

import java.util.UUID

object Ids {
    /** Demo ids look like "app_3f9a1c2e"; LIVE ids are server UUIDs. */
    fun new(prefix: String): String = prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(8)
    fun uuid(): String = UUID.randomUUID().toString()
}
