package mobappdev.example.sensorapplication.ui.viewmodels

/**
 * File: DataVM.kt
 * Purpose: Defines the view model of the data screen.
 *          Uses Dagger-Hilt to inject a controller model
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-07-11
 */

import android.content.ContentValues.TAG
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mobappdev.example.sensorapplication.domain.BluetoothDeviceDomain
import mobappdev.example.sensorapplication.domain.InternalSensorController
import mobappdev.example.sensorapplication.domain.PolarController
import java.io.File
import java.io.FileWriter
import java.io.IOException
import javax.inject.Inject


@HiltViewModel
class DataVM @Inject constructor(
    private val polarController: PolarController,
    private val internalSensorController: InternalSensorController,
): ViewModel() {

    val discoveredDevices = polarController.discoveredDevices
   // val scannedDevices: StateFlow<List<BluetoothDeviceDomain>> = polarController.scannedDevices

    private val gyroDataFlow = internalSensorController.currentGyroUI
    private val linAccDataFlow = internalSensorController.currentLinAccUI

    private val hrDataFlow = polarController.currentHR
    private val accDataFlow = polarController.currentAcceleration
    private val gyroDataFlowPolar = polarController.currentGyro
    private val ang1dataFlowPol = polarController.angleFromAlg1
    private val ang2dataFlowPol = polarController.angleFromAlg2

    val combinedPolarDataFlow = combine(
        polarController.angleFromAlg1,
        polarController.angleFromAlg2,
        polarController.timealg1,
        polarController.timealg2
    ) { angle1, angle2, time1, time2 ->
        CombinedPolarSensorData.AngleData(angle1, angle2,time1,time2)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // internal data flow
    val combinedInternalDataFlow = combine(
        internalSensorController.intAngleFromAlg1,
        internalSensorController.intAngleFromAlg2,
        internalSensorController.timeIntalg1,
        internalSensorController.timeIntalg2
    ) { intAngle1, intAngle2, timeInt1, timeInt2 ->
        internalSensorData.InternalAngles(intAngle1, intAngle2, timeInt1,timeInt2)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val combinedDataFlow = combine(
        gyroDataFlow,
        linAccDataFlow,
        hrDataFlow,
        accDataFlow,
        ang1dataFlowPol
    ) { gyro, linAcc, hr, acc, ang ->
        when {
            hr != null -> CombinedSensorData.HrData(hr)
            gyro != null -> CombinedSensorData.GyroData(gyro)
            acc != null -> CombinedSensorData.AccelerometerData(acc,ang)
            linAcc != null -> CombinedSensorData.InternalSensorData(linAcc)

            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /*
    private val _state = MutableStateFlow(DataUiState())
    
    val state = combine(
        polarController.scannedDevices,
        polarController.angleFromAlg1list,
        polarController.angleFromAlg2list,
        polarController.timealg1list,
        polarController.connected,
        _state,
    ) { scannedDevices, angleFromAlg1List, angleFromAlg2List, time1list , connected, state  ->
        state.copy(
            scannedDevices = scannedDevices ,
            angleFromAlg1List = angleFromAlg1List,
            angleFromAlg2List = angleFromAlg2List,
            time1PolList = time1list,
            connected = connected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)


     */

    private val _state = MutableStateFlow(DataUiState())

    val state = combine(
        polarController.angleFromAlg1list,
       // polarController.angleFromAlg2list,
        polarController.timealg1list,
        polarController.connected,
        _state,
        polarController.scannedDevices
        ) {  angleFromAlg1List, time1list, connected, state,scannedDevices ->
        state.copy(
            angleFromAlg1List = angleFromAlg1List,
            time1PolList = time1list,
            connected = connected,
            scannedDevices = scannedDevices
            )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)



    private val _internalState = MutableStateFlow(DataUiState())

    // todo: he intentat separar en un altre state: angles resultants son 0.
    val internalState = combine (
        internalSensorController.intAngleFromAlg1List,
        internalSensorController.intAngleFromAlg2List,
        internalSensorController.measuring,
        _internalState
    ) { intAngleFromAlg1List, intAngleFromAlg2List, measuring, state ->
        state.copy(
            intAngleFromAlg1List = intAngleFromAlg1List,
            intAngleFromAlg2List = intAngleFromAlg2List,
            measuring = measuring
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _internalState.value)


    private var streamType: StreamType? = null


    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String>
        get() = _deviceId.asStateFlow()


    fun chooseSensor(deviceId: String) {
        _deviceId.update { deviceId }
        connectToSensor()

    }

    // to do update to connect to selected device
    fun connectToSensor() {
        viewModelScope.launch {
            try {
                withTimeout(5000L) {
                    polarController.connectToDevice(_deviceId.value)
                    //_state.update { it.copy(connected = true) }
                    _stateConnection.update {
                        it.copy(
                            connected = polarController.connected.value,
                            //connecting = polarController.connecting.value
                        )
                    }
                }

            } catch (e: TimeoutCancellationException) {
                Log.d(TAG, "$e")
            }
        }
       // polarController.connectToDevice(_deviceId.value)
    }
    fun startDeviceDiscovery() {
        polarController.startDeviceDiscovery()
    }

    fun disconnectFromSensor() {
        stopDataStream()
        polarController.disconnectFromDevice(_deviceId.value)
    }

    fun startHr() {
        polarController.startHrStreaming(_deviceId.value)
        streamType = StreamType.FOREIGN_HR
        _state.update { it.copy(measuring = true) }
    }

    private val _stateConnection = MutableStateFlow(Connection())

    val stateConnection = combine(
        polarController.connected,
        //polarController.connecting,
        polarController.measuring,
        _stateConnection
    ) { connected, measuring, stateConnection ->
        stateConnection.copy(

            connected = connected,
            //connecting = connecting,
            measuring = measuring

        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _stateConnection.value)

    data class Connection(
        val connected: Boolean = false,
      //  val connecting: Boolean = false,
        val measuring: Boolean = false
    )

    fun startPolar(){
        polarController.startPolarStreaming(_deviceId.value)
        streamType = StreamType.FOREIGN
        _state.update { it.copy(measuring = true) }
    }

    fun startGyro() {
        internalSensorController.startGyroStream()
        streamType = StreamType.LOCAL_GYRO

        _state.update { it.copy(measuring = true) }
    }


    fun stopDataStream() {
        when (streamType) {
            StreamType.LOCAL_GYRO -> internalSensorController.stopGyroStream()
            StreamType.LOCAL_ACC -> internalSensorController.stopImuStream() // stop overall imu stream
            StreamType.FOREIGN_HR -> polarController.stopHrStreaming()
            StreamType.FOREIGN -> polarController.stopPolarStreaming()

            else -> {} // Do nothing
        }
        _state.update { it.copy(measuring = false) }
    }


    fun startImuStream() {
        internalSensorController.startImuStream()
        streamType = StreamType.LOCAL_ACC
        _state.update { it.copy(measuring = true) }

    }

    fun stopImuStream() {
        when (streamType) {
            StreamType.LOCAL_ACC -> internalSensorController.stopImuStream()
            else -> {}
        }
        _state.update { it.copy(measuring = false) }
    }


    //  state to track whether recording is in progress
    private val _recordingInProgress = MutableStateFlow(false)
    val recordingInProgress: StateFlow<Boolean>
        get() = _recordingInProgress.asStateFlow()

    // this is for the recording of data:
    private var recordingStartedTimestamp: Long = 0
    private val recordingDuration = 20 * 1000L // duration: 20 seconds

    fun startRecording() {
        // start recording logic
        if (!_recordingInProgress.value) {
            _recordingInProgress.value = true
            recordingStartedTimestamp = System.currentTimeMillis()
            _state.update { it.copy(measuring = true) }

            // Start a coroutine to check elapsed time and stop recording after 10 seconds
            viewModelScope.launch {
                while (_recordingInProgress.value) {
                    val elapsedTime = System.currentTimeMillis() - recordingStartedTimestamp
                    if (elapsedTime >= recordingDuration) {
                        // Stop recording after 20 seconds
                        stopRecording()
                        break
                    }
                    delay(100)
                }
            }
        }

    }


    fun stopRecording() {
        if (_recordingInProgress.value) {
            // stop recording logic
            recordingStartedTimestamp = 0
            _recordingInProgress.value = false // Set to false to stop the automatic recording
            _state.update { it.copy(measuring = false) }

            // Stop the streaming based on the stream type
            stopDataStream()

            // Export data after stopping recording
            saveCSVToFile()


        }
    }


    // function to save the CSV data to the file
    fun saveCSVToFile() {
        val csvFile = createCsvFile()

        try {
            FileWriter(csvFile).use { writer ->
                // Write data to the CSV file
                val polarAlg1List = state.value.angleFromAlg1List
                val internalAlg1List = state.value.intAngleFromAlg1List
                val time1Pollist = state.value.time1PolList
                val timeIntAlg1List = state.value.timeIntAlg1List

                writer.append("Polar Alg1, Internal Alg1, Time Polar, Time Int Alg1\n")

                for (i in polarAlg1List.indices){
                    val polarAlg1 = polarAlg1List.getOrNull(i) ?: 0.0
                    val internalAlg1 = internalAlg1List.getOrNull(i) ?: 0.0
                    val timeAlg1 = time1Pollist.getOrNull(i) ?: 0L
                    val timeIntAlg1 = timeIntAlg1List.getOrNull(i) ?: 0L

                    writer.append("$polarAlg1, $internalAlg1, , $timeAlg1, $timeIntAlg1 \n")
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun createCsvFile(): File {
        val fileName = "Sensor_data.csv"
        val downloadsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )

        if (!downloadsDir.parentFile.exists()) {
            downloadsDir.parentFile.mkdirs()
        }

        return downloadsDir
    }

}

data class DataUiState(
    val angleFromAlg1List: List<Float?> = emptyList(),
    val angleFromAlg2List: List<Float?> = emptyList(),
    val time1PolList: List<Long> = emptyList(),
    val intAngleFromAlg1List: List<Float> = emptyList(),
    val intAngleFromAlg2List: List<Float> = emptyList(),
    val timeIntAlg1List: List<Long?> = emptyList(),
    val timeIntAlg2List: List<Long?> = emptyList(),
    val connected: Boolean = false,
    val measuring: Boolean = false,
    val scannedDevices: List<BluetoothDeviceDomain> = emptyList(),

    )

enum class StreamType {
    LOCAL_GYRO, LOCAL_ACC, FOREIGN_HR, FOREIGN_ACC, FOREIGN
}

sealed class CombinedSensorData {
    data class GyroData(val gyro: Triple<Float, Float, Float>?) : CombinedSensorData()
    data class HrData(val hr: Int?) : CombinedSensorData()
    data class AccelerometerData(val acc: Triple<Float, Float, Float>?,val ang: Float?) : CombinedSensorData()
    data class InternalSensorData(val linAcc: Triple<Float, Float, Float>?) : CombinedSensorData()//, val angle1: Float?, val angle2: Float?) : CombinedSensorData()

}
sealed class CombinedPolarSensorData {
    data class AngleData(val angle1: Float?, val angle2: Float?,val time1: Long,val time2: Long) : CombinedPolarSensorData()
}

sealed class internalSensorData {
    data class InternalAngles(val intAngle1: Float?, val intAngle2: Float?, val timeInt1: Long,val timeInt2: Long) : internalSensorData()
}
