package com.myai.offline

import android.app.Application
import android.util.Log

class MyAIApplication : Application() {
    companion object {
        const val TAG = "MyAIApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MyAI Offline Application initialized.")
    }
}
