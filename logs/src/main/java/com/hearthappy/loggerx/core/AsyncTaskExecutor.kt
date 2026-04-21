package com.hearthappy.loggerx.core

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object AsyncTaskExecutor {
    private val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    private val threadIndex = AtomicInteger(1)
    private val workerFactory = ThreadFactory { runnable ->
        Thread(runnable, "loggerx-worker-${threadIndex.getAndIncrement()}").apply {
            isDaemon = true
        }
    }

    private val workerPool = ThreadPoolExecutor(
        cpuCount * 2,
        cpuCount * 2,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        workerFactory
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(
        2) { runnable ->
        Thread(runnable, "loggerx-scheduler-${threadIndex.getAndIncrement()}").apply {
            isDaemon = true
        }
    }

    fun execute(task: () -> Unit) {
        workerPool.execute(task)
    }

    fun submit(task: () -> Unit): Future<*> {
        return workerPool.submit(task)
    }

    fun schedule(delayMs: Long, task: () -> Unit): Future<*> {
        return scheduler.schedule(task, delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
    }

    fun getWorkerPoolSize(): Int = workerPool.corePoolSize
}
