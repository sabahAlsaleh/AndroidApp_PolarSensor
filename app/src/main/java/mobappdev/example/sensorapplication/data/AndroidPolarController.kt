package mobappdev.example.sensorapplication.data

/**
 * File: AndroidPolarController.kt
 * Purpose: Implementation of the PolarController Interface.
 *          Communicates with the polar API
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-07-11
 */

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mobappdev.example.sensorapplication.domain.BluetoothDeviceDomain
import mobappdev.example.sensorapplication.domain.FoundDeviceReceiver
import mobappdev.example.sensorapplication.domain.PolarController
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.sqrt
@SuppressLint("MissingPermission")
class AndroidPolarController(
    private val context: Context,
) : PolarController {

    private val api: PolarBleApi by lazy {
        // Notice all features are enabled
        PolarBleApiDefaultImpl.defaultImplementation(
            context = context,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            )
        )
    }

    private var hrDisposable: Disposable? = null
    private var accDisposable: Disposable? = null
    private var gyroDisposable: Disposable? = null
    private val TAG = "AndroidPolarController"
    private var selectedSensorId: String? = null

    private val _currentHR = MutableStateFlow<Int?>(null)
    override val currentHR: StateFlow<Int?>
        get() = _currentHR.asStateFlow()

    private val _hrList = MutableStateFlow<List<Int>>(emptyList())
    override val hrList: StateFlow<List<Int>>
        get() = _hrList.asStateFlow()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean>
        get() = _connected.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    override val connecting: StateFlow<Boolean>
        get() = _connecting.asStateFlow()

    private val _measuring = MutableStateFlow(false)
    override val measuring: StateFlow<Boolean>
        get() = _measuring.asStateFlow()

    private val _currentAcceleration = MutableStateFlow<Triple<Float, Float, Float>?>(null)
    override val currentAcceleration: StateFlow<Triple<Float, Float, Float>?>
        get() = _currentAcceleration.asStateFlow()

    private val _accelerationList = MutableStateFlow<List<Triple<Float, Float, Float>?>>(emptyList())
    override val accelerationList: StateFlow<List<Triple<Float, Float, Float>?>>
        get() = _accelerationList.asStateFlow()

    private val _currentGyro = MutableStateFlow<Triple<Float, Float, Float>?>(null)
    override val currentGyro: StateFlow<Triple<Float, Float, Float>?>
        get() = _currentGyro.asStateFlow()

    private val _gyroList = MutableStateFlow<List<Triple<Float, Float, Float>?>>(emptyList())
    override val gyroList: StateFlow<List<Triple<Float, Float, Float>?>>
        get() = _gyroList.asStateFlow()


    private val _angleFromAlg1 = MutableStateFlow<Float?>(null)
    override val angleFromAlg1: StateFlow<Float?>
        get() = _angleFromAlg1.asStateFlow()

    private val _angleFromAlg2 = MutableStateFlow<Float?>(null)
    override val angleFromAlg2: StateFlow<Float?>
        get() = _angleFromAlg2.asStateFlow()

    private val _angleFromAlg1List = MutableStateFlow<List<Float?>>(emptyList())
    override val angleFromAlg1list: StateFlow<List<Float?>>
        get() = _angleFromAlg1List.asStateFlow()

    private val _angleFromAlg2List = MutableStateFlow<List<Float?>>(emptyList())
    override val angleFromAlg2list: StateFlow<List<Float?>>
        get() = _angleFromAlg2List.asStateFlow()

    private val _timealg1 = MutableStateFlow<Long>(0L)
    override val timealg1: StateFlow<Long>
        get() = _timealg1.asStateFlow()

    private val _timealg2 = MutableStateFlow<Long>(0L)
    override val timealg2: StateFlow<Long>
        get() = _timealg2.asStateFlow()

    private val _timealg1list = MutableStateFlow<List<Long>>(emptyList())
    override val timealg1list: StateFlow<List<Long>>
        get() = _timealg1list.asStateFlow()

    private val _timealg2list = MutableStateFlow<List<Long>>(emptyList())
    override val timealg2list: StateFlow<List<Long>>
        get() = _timealg2list.asStateFlow()


    // bluetooth sitting
    private val bluetoothManager by lazy{
        context.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy{
        bluetoothManager?.adapter
    }
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<BluetoothDevice>>
        get() = _discoveredDevices.asStateFlow()



    init {
        api.setPolarFilter(false) //if true, only Polar devices are looked for

        val enableSdkLogs = false
        if(enableSdkLogs) {
            api.setApiLogger { s: String -> Log.d("Polar API Logger", s) }
        }

        api.setApiCallback(object: PolarBleApiCallback() {
            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d(TAG, "BATTERY LEVEL: $level")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTED: ${polarDeviceInfo.deviceId}")
                _connected.update { true }
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d(TAG, "DISCONNECTED: ${polarDeviceInfo.deviceId}")
                _connected.update { false }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d(TAG, "DIS INFO uuid: $uuid value: $value")
            }
        })
    }

    private val _scannedDevices= MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices= MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _pairedDevices.asStateFlow()

    private val foundDeviceReceiver = FoundDeviceReceiver{ device->
        _scannedDevices.update { devices->
            val newDevice = device.toBluetoothDeviceDomain()
            if (newDevice in devices) devices else devices + newDevice
        }
    }


    override fun startDeviceDiscovery() {
        //check permission

        context.registerReceiver(
            foundDeviceReceiver,
            IntentFilter(BluetoothDevice.ACTION_FOUND)
        )
        updatePairedDevices()
        bluetoothAdapter?.startDiscovery()

    }

    override fun stopDeviceDiscovery() {
        //check permission

        bluetoothAdapter?.cancelDiscovery()
    }

    override fun release() {
        context.unregisterReceiver(foundDeviceReceiver)
    }
    private fun updatePairedDevices(){
        //if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)){
        //  return}
        bluetoothAdapter
            ?.bondedDevices
            ?.map{ it.toBluetoothDeviceDomain()}
            ?.also{ devices->
                _pairedDevices.update { devices }}
    }


    override fun connectToDevice(deviceId: String) {
        _connected.update{true}

        try {
            api.connectToDevice(deviceId)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            Log.e(TAG, "Failed to connect to $deviceId.\n Reason $polarInvalidArgument")
        }
    }



    override fun disconnectFromDevice(deviceId: String) {
        try {
            api.disconnectFromDevice(deviceId)
        } catch (polarInvalidArgument: PolarInvalidArgument) {
            Log.e(TAG, "Failed to disconnect from $deviceId.\n Reason $polarInvalidArgument")
        }
    }

    override fun startHrStreaming(deviceId: String) {
        val isDisposed = hrDisposable?.isDisposed ?: true
        if(isDisposed) {
            _measuring.update { true }
            hrDisposable = api.startHrStreaming(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        for (sample in hrData.samples) {
                            _currentHR.update { sample.hr }
                            _hrList.update { hrList ->
                                hrList + sample.hr
                            }
                        }
                    },
                    { error: Throwable ->
                        Log.e(TAG, "Hr stream failed.\nReason $error")
                    },
                    { Log.d(TAG, "Hr stream complete")}
                )
        } else {
            Log.d(TAG, "Already streaming")
        }

    }

    override fun stopHrStreaming() {
        _measuring.update { false }
        hrDisposable?.dispose()
        _currentHR.update { null }
    }

    var startTime: Long? = 0L // Initialize the start time as nullable outside the loop

    private fun handleAccData(accData: PolarAccelerometerData) {
        for (sample in accData.samples) {
            if (startTime == 0L) {
                startTime = System.currentTimeMillis() // Set the start time with the current time in milliseconds
            }

            // Calculate the elapsed time in milliseconds since the start time
                val elapsedTime = System.currentTimeMillis() - (startTime ?: 0L)

            // Update _timealg1list with the elapsed time
            _timealg1list.update { timealg1list -> timealg1list + elapsedTime }

            // Set the elapsed time as the updated value for _timealg1
            _timealg1.update { elapsedTime }

            val accelerationTriple = Triple(sample.x.toFloat(), sample.y.toFloat(), sample.z.toFloat())
            _currentAcceleration.update { accelerationTriple }
            calculateAndApplyAngles() // Call here to calculate angles after receiving accelerometer data
        }
    }

    private fun handleGyroData(gyroData: PolarGyroData) {
        for (sample in gyroData.samples) {
            val timestamp = sample.timeStamp
            _timealg2.update { timestamp  }
            _timealg2list.update { timealg2list -> timealg2list + timestamp }
            val gyroTriple = Triple(sample.x, sample.y, sample.z)
            _currentGyro.update { gyroTriple }
            calculateAndApplyAngles() // Call here to calculate angles after receiving gyroscope data
        }
    }

    /**
     * Start streaming accelerometer and gyroscope data from a Polar device.
     * @param deviceId The ID of the Polar device to connect to.
     */
    override fun startPolarStreaming(deviceId: String) {
        // Check if either accelerometer or gyroscope is already streaming
        if (accDisposable?.isDisposed == false || gyroDisposable?.isDisposed == false) {
            Log.d(TAG, "Already streaming")
            return
        }

        // Set measuring state to true
        _measuring.update { true }

        // Define accelerometer sensor settings
        val accSensorSettings = mapOf(
            PolarSensorSetting.SettingType.CHANNELS to 3,
            PolarSensorSetting.SettingType.RANGE to 8,
            PolarSensorSetting.SettingType.RESOLUTION to 16,
            PolarSensorSetting.SettingType.SAMPLE_RATE to 52
        )
        val polarAccSettings = PolarSensorSetting(accSensorSettings)

        // Define gyroscope sensor settings
        val gyroSensorSettings = mapOf(
            PolarSensorSetting.SettingType.CHANNELS to 3,
            PolarSensorSetting.SettingType.RANGE to 2000,
            PolarSensorSetting.SettingType.RESOLUTION to 16,
            PolarSensorSetting.SettingType.SAMPLE_RATE to 52
        )
        val polarGyroSettings = PolarSensorSetting(gyroSensorSettings)

        // Start streaming accelerometer data
        accDisposable = api.startAccStreaming(deviceId, polarAccSettings)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { accData ->
                    handleAccData(accData)
                },
                { error ->
                    Log.e(TAG, "Acceleration stream failed.\nReason: $error")
                    // Implement specific error handling for accelerometer stream here
                },
                {
                    Log.d(TAG, "Acceleration stream complete")
                    // Implement actions after completion of the accelerometer stream here
                }
            )

        // Start streaming gyroscope data
        gyroDisposable = api.startGyroStreaming(deviceId, polarGyroSettings)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { gyroData ->
                    handleGyroData(gyroData)
                },
                { error ->
                    Log.e(TAG, "Gyro stream failed.\nReason $error")
                    // Implement specific error handling for gyroscope stream here
                },
                {
                    Log.d(TAG, "Gyro stream complete")
                    // Implement actions after completion of the gyroscope stream here
                }
            )
    }


    override fun calculateAndApplyAngles() {
        val acc = _currentAcceleration.value
        val gyro = _currentGyro.value

        if (acc != null && gyro != null) {
            val angleFromAlg1 = computeAngleOfElevation(acc.first, acc.second, acc.third)
            val angleFromAlg2 = calculateElevationAngle(
                acc.first, acc.second, acc.third,
                gyro.first, gyro.second, gyro.third
            )
            _angleFromAlg1.update { angleFromAlg1 }
            _angleFromAlg2.update { angleFromAlg2 }

            _angleFromAlg1List.update { angleFromAlg1List -> angleFromAlg1List + angleFromAlg1 }
            _angleFromAlg2List.update { angleFromAlg2List -> angleFromAlg2List + angleFromAlg2 }

        }
    }

    override fun stopPolarStreaming() {
        _measuring.update { false }
        accDisposable?.dispose()
        gyroDisposable?.dispose()
        _currentAcceleration.update { null }
        _currentGyro.update { null }
    }


    private var previousFilteredAngle: Float = 0.0f // Start with 0 degrees when parallel to the ground
    private val alpha: Float = 0.5f // Filter factor for the first function

    private fun computeAngleOfElevation(
        rawAccelerationX: Float,
        rawAccelerationY: Float,
        rawAccelerationZ: Float
    ): Float {
        val magnitude = sqrt(rawAccelerationX * rawAccelerationX + rawAccelerationY * rawAccelerationY)

        // Calculate angle of elevation in radians
        val angleRadians = atan2(rawAccelerationZ.toDouble(), magnitude.toDouble()).toFloat()

        // Convert radians to degrees
        val angleDegrees = Math.toDegrees(angleRadians.toDouble()).toFloat()

        // Apply EWMA filter
        val currentFilteredAngle = alpha * angleDegrees + (1 - alpha) * previousFilteredAngle

        // Update previous filtered angle for the next iteration
        previousFilteredAngle = currentFilteredAngle

        // Map the angle to a range from 0 to 180 degrees
        return currentFilteredAngle.absoluteValue
    }


    private val alpha2: Float = 0.9f // Filter factor for the second function

    private fun calculateElevationAngle(
        rawAccelerationX: Float,
        rawAccelerationY: Float,
        rawAccelerationZ: Float,
        rawGyroX: Float,
        rawGyroY: Float,
        rawGyroZ: Float
    ): Float {
        // Apply the filter to raw linear acceleration and gyroscope data
        val filteredAccelerationX = alpha2 * rawAccelerationX + (1 - alpha2) * rawGyroX
        val filteredAccelerationY = alpha2 * rawAccelerationY + (1 - alpha2) * rawGyroY
        val filteredAccelerationZ = alpha2 * rawAccelerationZ + (1 - alpha2) * rawGyroZ

        // Calculate the magnitude of the acceleration vector
        val magnitude = sqrt(
            filteredAccelerationX * filteredAccelerationX +
                    filteredAccelerationY * filteredAccelerationY)

        // Calculate the pitch angle (elevation angle) using trigonometric functions
        val pitchRadians = atan2(filteredAccelerationZ, magnitude)

        // Adjust the angle based on orientation (perpendicular: 90 degrees, parallel: 0 degrees)
        val adjustedAngle = Math.toDegrees(pitchRadians.toDouble()).toFloat()

        // If sensor is parallel to the ground, return 0 degrees; if perpendicular, return 90 degrees
        return adjustedAngle.absoluteValue
    }
}