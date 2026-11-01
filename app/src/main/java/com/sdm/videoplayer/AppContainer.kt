package com.sdm.videoplayer

import android.content.Context
import com.sdm.videoplayer.data.db.AppDatabase
import com.sdm.videoplayer.data.prefs.SettingsStore
import com.sdm.videoplayer.data.repo.UserDataRepository
import com.sdm.videoplayer.data.repo.VideoRepository

/** حاوية اعتماديات بسيطة (بدون مكتبات DI خارجية). */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.get(context)
    val settings = SettingsStore(context)
    val videoRepository = VideoRepository(context)
    val userData = UserDataRepository(database)
}
