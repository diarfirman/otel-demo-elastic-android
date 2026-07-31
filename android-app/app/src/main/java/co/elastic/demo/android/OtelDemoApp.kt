package co.elastic.demo.android

import android.app.Application
import co.elastic.apm.android.sdk.ElasticApmAgent
import co.elastic.apm.android.sdk.ElasticApmConfiguration

class OtelDemoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initElasticApm()
    }

    private fun initElasticApm() {
        val serverUrl = BuildConfig.ELASTIC_APM_SERVER_URL
        if (serverUrl.isNullOrEmpty()) {
            return
        }

        val config = ElasticApmConfiguration.builder().build()
        ElasticApmAgent.initialize(this as Application, config)
    }
}
