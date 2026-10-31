package com.daytrace

import android.app.Application
import com.daytrace.work.WorkScheduler

class DayTraceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val container = ServiceLocator.get(this)
        container.notifier.ensureChannels()
        container.goalNotifier.ensureChannel()
        WorkScheduler.schedule(this)
    }
}
