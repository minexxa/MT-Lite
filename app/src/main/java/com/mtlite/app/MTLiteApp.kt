package com.mtlite.app

import android.app.Application
import com.mtlite.app.util.FileHelper
import com.topjohnwu.superuser.Shell

class MTLiteApp : Application() {
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
