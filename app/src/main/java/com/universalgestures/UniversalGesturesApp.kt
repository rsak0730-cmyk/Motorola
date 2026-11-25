package com.universalgestures

import android.app.Application
import com.universalgestures.data.DiagnosticsStore
import com.universalgestures.service.DiagnosticsHolder

class UniversalGesturesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticsHolder.store = DiagnosticsStore()
    }
}
