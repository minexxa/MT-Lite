package com.ma.manager

import android.app.Application
import com.ma.manager.util.FileHelper
import com.topjohnwu.superuser.Shell

class MAApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Configure libsu
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
        // Zip helpers need a Context for remembered archive passwords
        FileHelper.init(this)
    }
}
