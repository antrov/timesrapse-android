package com.antrov.timesrapse.utils.xlog

import android.os.Build
import android.util.Log.d
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.*
import com.antrov.timesrapse.BuildConfig
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.Printer
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.HashMap


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
    @SerializedName("lvl") val level: String,
    @SerializedName("tag") val tag: String?,
    @SerializedName("msg") val msg: String?
)

interface Promtail {
    var isEnabled: Boolean
}

class PromtailPrinter(private var logLevel: Int, private val cacheDir: File) : Printer, Promtail {

    override var isEnabled: Boolean = true
        set(value) {
            if (value == field) return
            XLog.v("promtail isEnabled changed to $value")
            field = value
        }

    private val queue: RequestQueue by lazy {
        val cache = DiskBasedCache(cacheDir, 1024 * 1024)
        val network = BasicNetwork(HurlStack())

        RequestQueue(cache, network).apply { start() }
    }

    private val reqHeaders: HashMap<String, String> by lazy {
        val auth = Base64.getEncoder()
            .encodeToString("${BuildConfig.PROMTAIL_USER}:${BuildConfig.PROMTAIL_KEY}".toByteArray())

        hashMapOf(
            "Authorization" to "Basic $auth",
            "Content-Type" to "application/json"
        )
    }

    override fun println(logLevel: Int, tag: String?, msg: String?) {
        if (logLevel < this.logLevel || !isEnabled) return

        val log = Log(LogLevel.getShortLevelName(logLevel), tag, msg)

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

        val request = object : StringRequest(Method.POST, BuildConfig.PROMTAIL_URL,
            Response.Listener { },
            Response.ErrorListener { }) {
            override fun getBody(): ByteArray {
                return Gson().toJson(event).toByteArray(Charsets.UTF_8)
            }

            override fun getHeaders(): MutableMap<String, String> {
                return reqHeaders
            }
        }

        queue.add(request)
    }

}
//
//class NetworkStateReceiver : BroadcastReceiver() {
//    override fun onReceive(context: Context, intent: Intent) {
//        Log.d(TAG, "Network connectivity change")
//        if (intent.extras != null) {
//            val connectivityManager = context
//                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//            val ni = connectivityManager.activeNetworkInfo
//            if (ni != null && ni.state == NetworkInfo.State.CONNECTED) {
//                //Network becomes available
//                Log.i(
//                    TAG,
//                    "Network " + ni.typeName + " connected"
//                )
//            } else if (intent.getBooleanExtra(
//                    ConnectivityManager.EXTRA_NO_CONNECTIVITY,
//                    Boolean.FALSE
//                )
//            ) {
//                Log.d(
//                    TAG,
//                    "There's no network connectivity"
//                )
//            }
//        }
//    }
//
//    companion object {
//        private const val TAG = "NetworkStateReceiver"
//    }
//}