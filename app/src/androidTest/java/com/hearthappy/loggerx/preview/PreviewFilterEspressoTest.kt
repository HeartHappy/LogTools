package com.hearthappy.loggerx.preview

import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hearthappy.log.LoggerX
import com.hearthappy.loggerx.R
import org.hamcrest.CoreMatchers.containsString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewFilterEspressoTest {

    @Before
    fun prepareData() {
        LoggerX.COMMON.clearAllLogs()
        TagALogger.logDebug()
        TagBLogger.logInfo()
        TagALogger.logDebug()
        TagBLogger.logInfo()
    }



    private object TagALogger {
        fun logDebug() {
            LoggerX.COMMON.d("Tag A message")
        }
    }

    private object TagBLogger {
        fun logInfo() {
            LoggerX.COMMON.i("Tag B message")
        }
    }
}
