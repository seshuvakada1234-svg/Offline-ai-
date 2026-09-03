package com.myai.offline

import android.app.Application
import android.util.Log

class MyAIApplication : Application() {
    companion object {
        const val TAG = "MyAIApplication"
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val prefs = getSharedPreferences("myai_crash_log", MODE_PRIVATE)
                prefs.edit()
                    .putString("last_crash_error", "${throwable.javaClass.simpleName}: ${throwable.message}")
                    .putString("last_crash_stack", Log.getStackTraceString(throwable))
                    .putLong("last_crash_time", System.currentTimeMillis())
                    .commit()
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
        Log.i(TAG, "MyAI Offline Application initialized.")
    }
}
