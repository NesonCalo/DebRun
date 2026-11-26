package com.nesoncalo.debrun

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nesoncalo.debrun.core.Prefs
import com.nesoncalo.debrun.ui.AppRoot
import com.nesoncalo.debrun.ui.theme.DebRunTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val tag = Prefs.language
        if (tag.isNullOrBlank()) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DebRunTheme {
                AppRoot()
            }
        }
    }
}
