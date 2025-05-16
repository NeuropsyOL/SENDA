package de.uol.neuropsy.senda.ui.main

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import de.uol.neuropsy.senda.data.SensorRepositoryImpl
import de.uol.neuropsy.senda.sensor.MovellaBridge
import de.uol.neuropsy.senda.ui.state.UiState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.runner.Description
import org.junit.rules.TestWatcher
// MainViewModelTest.kt
@ExperimentalCoroutinesApi
class MainViewModelTest {

    // A rule to set Dispatchers.Main to a TestDispatcher
    @get:Rule
    val coroutinesRule = MainDispatcherRule()

    private lateinit var repository: SensorRepositoryImpl
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        repository = mockk()
        // Use an ApplicationProvider or a FakeApplication
        vm = MainViewModel(ApplicationProvider.getApplicationContext(), repository)
    }

    @Test
    fun `startScan emits scanning then discovered`() = runTest {
        val onboard = listOf("Accelerometer", "Gyroscope")
        val movellaBridges = listOf<MovellaBridge>()  // no BLE devices

        every { repository.getAvailableOnboardSensors() } returns onboard
        coEvery { repository.scanForMovellaDevices() } returns flowOf(movellaBridges)

        vm.uiState
            .test {
                // initial Idle
                assertIs<Any>(awaitItem())
                vm.startScan()

                // Scanning
                assertIs<UiState.Scanning>(awaitItem())

                // Discovered with just onboard
                val discovered = awaitItem() as UiState.DevicesDiscovered
                assertEquals(onboard, discovered.onboardSensors)
                assertEquals(/* expected = */ emptyList<MovellaBridge>(), /* actual = */ discovered.movellaDevices)

                cancelAndIgnoreRemainingEvents()
            }
    }

    private fun <T> assertIs(awaitItem: UiState) {
        TODO("Not yet implemented")
    }

    @Test fun `startSelectedSensors with one Dot skips sync and streams`() = runTest {
        val selected = listOf("Accelerometer")
        every { repository.getAvailableOnboardSensors() } returns emptyList()
        coEvery { repository.syncMovellaDevices(any()) } returns flow { /* never emits */ }
        coEvery { repository.startStreaming(selected) } returns flowOf(true)

        vm.uiState
            .test {
                assertIs<UiState.Idle>(awaitItem())

                vm.startSelectedSensors(selected)

                // Should immediately go to Streaming (no sync needed)
                assertIs<UiState.Streaming>(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test fun `startSelectedSensors error from streaming emits Error`() = runTest {
        val selected = listOf("Accelerometer")
        coEvery { repository.getAvailableOnboardSensors() } returns emptyList()
        coEvery { repository.syncMovellaDevices(any()) } returns flow { /* skip */ }
        coEvery { repository.startStreaming(selected) } returns flowOf(false)

        vm.uiState
            .test {
                assertIs<UiState.Idle>(awaitItem())

                vm.startSelectedSensors(selected)

                // Streaming state first
                assertIs<UiState.Streaming>(awaitItem())
                // Then Error
                val err = awaitItem() as UiState.Error
                assertTrue(err.message.contains("Failed"))

                cancelAndIgnoreRemainingEvents()
            }
    }
}

@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}