package com.dailyworks.apnalaundry

import android.app.Application
import com.dailyworks.apnalaundry.data.LaundryRepository
import com.dailyworks.apnalaundry.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ApnaLaundryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ApnaLaundryApp)
            modules(appModule)
        }
        // Seed the demo dataset on first launch.
        val repo: LaundryRepository = get()
        CoroutineScope(Dispatchers.IO).launch { repo.ensureSeeded() }
    }
}
