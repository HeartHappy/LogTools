package com.hearthappy.logs

import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.xml.transform.OutputKeys
import javax.xml.transform.Source
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

internal class LogImpl(private var scope: String , private val interceptor: LogInterceptor) : ILog {
    private val localTag = ThreadLocal<String>()
    private var tag: String = "LogTools"

    private lateinit var logStrategy: LogStrategy

    private val date: Date by lazy { Date() }
    private val dateFormat: SimpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.CHINESE) }

    override fun t(tag: String): ILog {
        localTag.set(tag)
        return this
    }

    override fun d(message: String, vararg args: Any?) {
        log(Log.DEBUG, null, message, args)
    }

    override fun d(obz: Any?) {
        log(Log.DEBUG, null, Utils.toString(obz))
    }

    override fun e(message: String, vararg args: Any?) {
        e(null, message, *args)
    }

    override fun e(throwable: Throwable?, message: String, vararg args: Any?) {
        log(Log.ERROR, throwable, message, args)
    }

    override fun w(message: String, vararg args: Any?) {
        log(Log.WARN, null, message, args)
    }

    override fun i(message: String, vararg args: Any?) {
        log(Log.INFO, null, message, args)
    }

    override fun v(message: String, vararg args: Any?) {
        log(Log.VERBOSE, null, message, args)
    }

    override fun wtf(message: String, vararg args: Any?) {
        log(Log.ASSERT, null, message, args)
    }

    override fun json(json: String?) {
        json?.let { j ->
            try {
                val result = j.trim { it <= ' ' }
                if (result.startsWith("{")) {
                    val jsonObject = JSONObject(result)
                    val message = jsonObject.toString(JSON_INDENT)
                    d(message)
                    return
                }
                if (result.startsWith("[")) {
                    val jsonArray = JSONArray(result)
                    val message = jsonArray.toString(JSON_INDENT)
                    d(message)
                    return
                }
                e("Invalid Json")
            } catch (e: JSONException) {
                e("Invalid Json")
            }
        } ?: d("Empty/Null json content")
    }

    override fun xml(xml: String?) {
        xml?.let {
            try {
                val xmlInput: Source = StreamSource(StringReader(it))
                val xmlOutput = StreamResult(StringWriter())
                val transformer = TransformerFactory.newInstance().newTransformer()
                transformer.setOutputProperty(OutputKeys.INDENT, "yes")
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                transformer.transform(xmlInput, xmlOutput)
                d(xmlOutput.writer.toString().replaceFirst(">".toRegex(), ">\n"))
            } catch (e: TransformerException) {
                e("Invalid xml")
            }
        } ?: d("Empty/Null xml content")
    }

    @Synchronized
    override fun log(priority: Int, onceOnlyTag: String?, message: String?, throwable: Throwable?) {
        var logMsg = ""
        if (throwable != null && message != null) {
            logMsg = "$message : " + Utils.getStackTraceString(throwable)
        }
        if (throwable != null && message == null) {
            logMsg = Utils.getStackTraceString(throwable)
        }
        val tag: String = formatTag(onceOnlyTag)

        date.time = System.currentTimeMillis()

        val builder = StringBuilder()

        if (interceptor.isWriteFile()) {

            // machine-readable date/time
            builder.append(date.time.toString())
            builder.append(SEPARATOR)

            // human-readable date/time
            builder.append(dateFormat.format(date))
            builder.append(SEPARATOR)
        }

        // level
        builder.append(Utils.logLevel(priority))
        builder.append(SEPARATOR)

        // tag
        builder.append(tag)
        message?.let { logMsg = it } ?: let { logMsg = "Empty/NULL log message" } // message

        builder.append(SEPARATOR)
        builder.append(logMsg)

        // new line
        builder.append(NEW_LINE)

        if (interceptor.isWriteFile()) { //创建磁盘对象
            if (!::logStrategy.isInitialized) {
                val diskPath = Environment.getExternalStorageDirectory().absolutePath
                val folder = diskPath + File.separatorChar + "logger"
                val ht = HandlerThread("AndroidFileLogger.$folder")
                ht.start()
                val handler: Handler = DiskLogStrategy.WriteHandler(ht.looper, folder, MAX_BYTES, scope)
                logStrategy = DiskLogStrategy(handler)
            }
            logStrategy.log(priority, tag, builder.toString())
        } else {
            android.util.Log.d(tag, builder.toString())
        }
    }

    override fun clear(scope: String): Boolean {
        val diskPath = Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath + File.separatorChar + "logger"
        val results = arrayListOf<Boolean>()
        val map = File(folder).listFiles()?.mapNotNull { if (it.name.contains(scope)) it else null }
        map?.forEachIndexed { _, file ->
            results.add(file.delete())
            println("$tag,delete $scope succeed")
        } ?: println("$tag,delete $scope fail,not found")
        val find = results.find { !it }
        return find ?: true
    }

    override fun clearAll(): Boolean {
        val diskPath = Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath + File.separatorChar + "logger"
        val results = arrayListOf<Boolean>()
        File(folder).listFiles()?.forEach { results.add(it.delete()) }
        val find = results.find { !it }
        return find ?: true
    }


    @Synchronized
    private fun log(priority: Int, throwable: Throwable?, msg: String, vararg args: Any) {
        val tag = getTag()
        val message = createMessage(msg, *args)
        log(priority, tag, message, throwable)
    }

    private fun formatTag(tag: String?): String {
        return tag?.run { this } ?: this.tag
    }

    /**
     * @return the appropriate tag based on local or global
     */
    private fun getTag(): String? {
        val tag = localTag.get()
        if (tag != null) {
            localTag.remove()
            return tag
        }
        return null
    }

    private fun createMessage(message: String, vararg args: Any): String {
        return if (args.isEmpty()) message else String.format(message, *args)
    }

    companion object {
        private const val SEPARATOR = ","
        private const val JSON_INDENT = 2
        private val NEW_LINE = System.getProperty("line.separator")
        private const val NEW_LINE_REPLACEMENT = " <br> "

        const val MAX_BYTES = 500 * 1024 // 500K averages to a 4000 lines per file

    }
}