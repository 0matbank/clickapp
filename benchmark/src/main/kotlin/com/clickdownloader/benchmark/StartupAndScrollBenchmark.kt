package com.clickdownloader.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupAndScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldStartup() = startup(StartupMode.COLD)
    @Test fun warmStartup() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = mode,
        iterations = 5,
    ) { pressHome(); startActivityAndWait() }

    @Test fun homeScrollFrames() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); startActivityAndWait() },
    ) {
        val tabY = device.displayHeight - (device.displayHeight / 20)
        device.click(device.displayWidth * 3 / 8, tabY)
        device.waitForIdle()
        device.click(device.displayWidth / 8, tabY)
        device.waitForIdle()
    }

    private companion object { const val PACKAGE = "com.clickdownloader.app" }
}
