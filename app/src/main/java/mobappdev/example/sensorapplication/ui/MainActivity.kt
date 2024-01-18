package mobappdev.example.sensorapplication.ui

/**
 * File: MainActivity.kt
 * Purpose: Defines the main activity of the application.
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-09-21
 */

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarSensorSetting
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import mobappdev.example.sensorapplication.ui.screens.BluetoothDataScreen
import mobappdev.example.sensorapplication.ui.screens.GraphScreen
import mobappdev.example.sensorapplication.ui.screens.SensorSelectionScreen
import mobappdev.example.sensorapplication.ui.theme.SensorapplicationTheme
import mobappdev.example.sensorapplication.ui.viewmodels.DataVM

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy{
        applicationContext.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy{
        bluetoothManager?.adapter
    }
    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled==true

    /*
   companion object {
       private const val TAG = "MainActivity"
       private const val API_LOGGER_TAG = "API LOGGER"
       private const val PERMISSION_REQUEST_CODE = 1
   }


   private val api: PolarBleApi by lazy {
       // Notice all features are enabled
       PolarBleApiDefaultImpl.defaultImplementation(
           this,
           setOf(
               PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
               PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
               PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
               PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
               PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
               PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
               PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
               PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
           )
       )
   }

   fun requestStreamSettings(identifier: String, feature: PolarBleApi.PolarDeviceDataType): Flowable<PolarSensorSetting> {
       val availableSettings = api.requestStreamSettings(identifier, feature)
       val allSettings = api.requestFullStreamSettings(identifier, feature)
           .onErrorReturn { error: Throwable ->
               Log.w(TAG, "Full stream settings are not available for feature $feature. REASON: $error")
               PolarSensorSetting(emptyMap())
           }
       return Single.zip(availableSettings, allSettings) { available: PolarSensorSetting, all: PolarSensorSetting ->
           if (available.settings.isEmpty()) {
               throw Throwable("Settings are not available")
           } else {
               Log.d(TAG, "Feature " + feature + " available settings " + available.settings)
               Log.d(TAG, "Feature " + feature + " all settings " + all.settings)
               return@zip available
           }
       }
           .observeOn(AndroidSchedulers.mainThread())
           .toFlowable()

   }

    */


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 31)
            } else {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 30)
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 29)
        }
        val enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){}
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ){
                perms->val canEnableBluetooth = perms[Manifest.permission.BLUETOOTH_CONNECT] == true

            if (canEnableBluetooth && !isBluetoothEnabled){
                enableBluetoothLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )
            }
        }
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        )

        setContent {
            SensorapplicationTheme {
                val dataVM = hiltViewModel<DataVM>()

                // Use hardcoded deviceID
                //dataVM.chooseSensor(deviceId)

                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Myapp(dataVM)
                }
            }
        }
    }

}

@Composable
fun Myapp(dataViewModel: DataVM){
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "BluetoothDataScreen"
    ){

        composable("BluetoothDataScreen"){
            BluetoothDataScreen(vm = dataViewModel, navController = navController)
        }
        composable("SensorSelectionScreen"){
            SensorSelectionScreen(vm = dataViewModel, navController = navController)

        }
        composable("GraphScreen"){
            GraphScreen(vm = dataViewModel, navController = navController)
        }

    }
}

