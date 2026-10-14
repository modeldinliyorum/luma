package com.luma.reader

import android.app.Application
import android.content.Context
import com.luma.reader.data.LibraryRepository
import com.luma.reader.data.LumaDatabase
import com.luma.reader.data.SettingsStore

class LumaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-rolled dependency container. Small app, no DI framework needed. */
class AppContainer(context: Context) {
    private val database = LumaDatabase.get(context)
    val repository = LibraryRepository(database.bookDao(), database.highlightDao())
    val settings = SettingsStore(context)
}
