package com.hearthappy.log.core

import android.util.Log
import com.hearthappy.log.LoggerX
import com.hearthappy.log.db.LogDbManager
import com.hearthappy.log.interceptor.LogInterceptor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.OutputKeys
import javax.xml.transform.Source
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

internal class LogImpl(private var scope: LogScope, private val interceptor: LogInterceptor): ILog {

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

    @Synchronized override fun log(level: Int, message: String?, throwable: Throwable?) {
        val msg = message ?: return
        val stackTraceInfo = LogContextCollector.getStackTraceInfo()
        val logLevel = Utils.logLevel(level)
        val tag: String = formatTag(stackTraceInfo.className)
        val methodName = LogFormatter.format(stackTraceInfo)
        val logMsg = throwable?.run { "$msg : $NEW_LINE" + Utils.getStackTraceString(this) } ?: msg
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
        if (interceptor.isWriteDatabase()) {
            LogDbManager.insertLog(scopeTag = scope.getTag(), level = logLevel, classTag = tag, method = methodName, message = logMsg)
        }
    }


    @Synchronized
    private fun log(priority: Int, throwable: Throwable?, msg: String, vararg args: Any) {
        val message = createMessage(msg, *args)
        log(priority, message, throwable)
    }

    private fun formatTag(tag: String?): String {
        return tag?.run { this } ?: LoggerX.TAG
    }

    private fun createMessage(message: String, vararg args: Any): String {
        return if (args.isEmpty()) message else String.format(message, *args)
    }

    companion object {
        private const val JSON_INDENT = 2
        private val NEW_LINE = System.lineSeparator()

    }
}
