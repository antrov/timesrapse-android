package com.antrov.timesrapse

import android.app.Activity
import android.content.Context
import android.os.Build

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


data class Event(@SerializedName("streams") val streams: List<Stream>)

data class Stream(
    @SerializedName("stream") val labels: Labels,
    @SerializedName("values") val values: List<List<String>>
)

data class Labels(
    @SerializedName("app") val app: String,
    @SerializedName("build") val build: String,
    @SerializedName("device") val device: String
)

data class Log(
    @SerializedName("success") val success: Boolean,
    @SerializedName("succeeded") val succeeded: Int,
    @SerializedName("failed") val failed: Int
)

class Promtail(private val context: Context) {

    companion object {
        const val sharedPrefName = "com.antrov.timesrapse.promtail"
        const val sharedPrefUser = "user"
        const val sharedPrefKey = "key"
        const val sharedPrefPromtailEnabled = "enbaled"
    }

    fun log(success: Boolean, succeeded: Int, failed: Int) {
        val prefs = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE).also {
            if (!it.getBoolean(sharedPrefPromtailEnabled, false)) return@log
        }

        val user = prefs.getString(sharedPrefUser, "").also {
            if (it.isNullOrEmpty()) return@log
        }

        val key = prefs.getString(sharedPrefKey, "").also {
            if (it.isNullOrEmpty()) return@log
        }

        val log = Log(success, succeeded, failed)

        val event = Event(
            listOf(
                Stream(
                    labels = Labels(
                        app = BuildConfig.APPLICATION_ID,
                        build = BuildConfig.TIMESTAMP.toString(),
                        device = Build.DEVICE
                    ),
                    values = listOf(
                        listOf(
                            "${System.currentTimeMillis()}000000",
                            Gson().toJson(log)
                        )
                    )
                )
            )
        )

        Thread(Runnable {
            val url = URL("https://logs-prod-us-central1.grafana.net/loki/api/v1/push")

            with(url.openConnection() as HttpURLConnection) {
                requestMethod = "POST"

                val auth = Base64.getEncoder().encodeToString("$user:$key".toByteArray())

                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Basic $auth")

                val wr = OutputStreamWriter(outputStream);
                wr.write(Gson().toJson(event));
                wr.flush();

                Log.d("http", Gson().toJson(event))
                Log.d("http", "URL : $url")
                Log.d("http", "Response Code : $responseCode")

                disconnect()
            }
        }).start()
    }
}