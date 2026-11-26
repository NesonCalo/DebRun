package com.nesoncalo.debrun

import android.app.Application
import com.nesoncalo.debrun.core.Env

class DebRunApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Env.init(this)
    }
}
