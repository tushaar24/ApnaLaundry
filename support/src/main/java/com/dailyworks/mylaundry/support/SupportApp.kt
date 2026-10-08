package com.dailyworks.mylaundry.support

import android.app.Application
import com.dailyworks.mylaundry.support.data.CallFlow
import com.dailyworks.mylaundry.support.data.CallMonitor
import com.dailyworks.mylaundry.support.data.Prefs
import com.dailyworks.mylaundry.support.data.RecordingFinder
import com.dailyworks.mylaundry.support.data.SupportApi

/** The app's few singletons (small enough not to need a DI framework). */
class Graph(app: Application) {
    val api = SupportApi()
    val prefs = Prefs(app)
    val monitor = CallMonitor(app)
    val finder = RecordingFinder(app)
    val calls = CallFlow(app, api, prefs, monitor, finder)
}

class SupportApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }
}
