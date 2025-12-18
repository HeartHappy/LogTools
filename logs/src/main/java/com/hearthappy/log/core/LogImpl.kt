package com.hearthappy.log.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import com.hearthappy.log.interceptor.LogInterceptor
import com.hearthappy.log.strategy.DiskLogStrategy
import com.hearthappy.log.strategy.LogStrategy
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.Date
import javax.xml.transform.OutputKeys
import javax.xml.transform.Source
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

internal class LogImpl(private var scope: LogScope, private val interceptor: LogInterceptor, private val context: Context?, private val diskPath: String?) : ILog {


    private val logStrategy: LogStrategy by lazy { initLogStrategy() }

    private fun initLogStrategy(): DiskLogStrategy {
        val diskPath = this@LogImpl.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath + File.separatorChar + "logger"
        val ht = HandlerThread("AndroidFileLogger.$folder")
        ht.start()
        val handler: Handler = DiskLogStrategy.WriteHandler(ht.looper, folder, MAX_BYTES, scope.getTag())
        return DiskLogStrategy(handler)
    }



    override fun d(message: String, vararg args: Any?) {
        log(Log.DEBUG, null, message, args)
    }

    override fun d(obj: Any?) {
        log(Log.DEBUG, null, Utils.toString(obj))
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
                e("Invalid Json:${e.message}")
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
                e("Invalid xml:${e.message}")
            }
        } ?: d("Empty/Null xml content")
    }

    @Synchronized
    override fun log(level: Int, message: String?, throwable: Throwable?) {
        val msg = message ?: return
        val stackTraceInfo = LogContextCollector.getStackTraceInfo()
        val tag: String = formatTag(stackTraceInfo.className)
        if (interceptor.isDebug()) {
            when (level) {
                Log.VERBOSE -> Log.v(tag, msg)
                Log.DEBUG -> Log.d(tag, msg)
                Log.INFO -> Log.i(tag, msg)
                Log.WARN -> Log.w(tag, msg)
                Log.ERROR -> Log.e(tag, msg)
                Log.ASSERT -> Log.wtf(tag, msg)
            }
        }
        if (interceptor.isWriteFile()) {

            val methodName = LogFormatter.format(stackTraceInfo)
            val logMsg = throwable?.run { "$msg : $NEW_LINE" + Utils.getStackTraceString(this) } ?: msg
            val builder = StringBuilder()
            builder.append(LogFormatter.DATE_FORMAT.format(Date()))
            builder.append(SEPARATOR)

            // level
            builder.append(Utils.logLevel(level))
            builder.append(SEPARATOR)

            // tag
            builder.append(tag)
            builder.append(SEPARATOR)

            // method
            builder.append(methodName)
            builder.append(SEPARATOR)

            //format message
            builder.append(logMsg)

            // new line
            builder.append(NEW_LINE)
            context?.apply {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    logStrategy.log(level, tag, builder.toString())
                } else {
                    e(tag, "No file write permission")
                }
            } ?: e(tag, "The logging framework does not obtain the application context")
        }
    }


    @Synchronized
    private fun log(priority: Int, throwable: Throwable?, msg: String, vararg args: Any) {
        val message = createMessage(msg, *args)
        log(priority, message, throwable)
    }

    private fun formatTag(tag: String?): String {
        return tag?.run { this } ?: LogManager.TAG
    }

    private fun createMessage(message: String, vararg args: Any): String {
        return if (args.isEmpty()) message else String.format(message, *args)
    }

    companion object {
        private const val SEPARATOR = ","
        private const val JSON_INDENT = 2
        private val NEW_LINE = System.lineSeparator()
//        private const val NEW_LINE_REPLACEMENT = " <br> "

        const val MAX_BYTES = 100 * 1024 // 500K averages to a 4000 lines per file

    }
}