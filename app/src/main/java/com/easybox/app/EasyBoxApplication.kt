package com.easybox.app

import android.app.Application

class EasyBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: EasyBoxApplication
            private set
    }
}
