package com.tff.app.data.demo

import android.content.Context
import com.tff.app.data.Repo
import com.tff.app.data.StubRepo
import com.tff.app.model.AppMode

/** PLACEHOLDER — replaced by WS3 (offline demo: seed + JSON store + simulator). */
class DemoRepo(context: Context) : Repo by StubRepo(AppMode.DEMO)
