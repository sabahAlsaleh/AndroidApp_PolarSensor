package mobappdev.example.sensorapplication.ui.screens

/**
 * File: BluetoothDataScreen.kt
 * Purpose: Defines the UI of the data screen.
 * Author: Jitse van Esch
 * Created: 2023-07-08
 * Last modified: 2023-07-11
 */

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import mobappdev.example.sensorapplication.ui.viewmodels.CombinedPolarSensorData
import mobappdev.example.sensorapplication.ui.viewmodels.CombinedSensorData
import mobappdev.example.sensorapplication.ui.viewmodels.DataVM
import mobappdev.example.sensorapplication.ui.viewmodels.internalSensorData
import java.io.File


@Composable
fun BluetoothDataScreen(
    vm: DataVM, navController: NavController
) {
    val state = vm.state.collectAsStateWithLifecycle().value
    val internalState = vm.internalState.collectAsStateWithLifecycle().value
    val deviceId = vm.deviceId.collectAsStateWithLifecycle().value

    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val csvFiles: List<File> = directory.listFiles { file -> file.extension == "csv" }?.toList() ?: emptyList()
    val fileNames: List<String> = csvFiles.map { it.name }



    val recordingInProgress by vm.recordingInProgress.collectAsState()


    var polarConnected by remember { mutableStateOf<Boolean>(false) }
    polarConnected = remember(state.connected) {
        derivedStateOf {
            state.connected
        }.value
    }
    var internalConnected by remember { mutableStateOf<Boolean>(false) }
        internalConnected = remember(state.connected) {
        !state.connected
    }

    //var internalRunning by remember { mutableStateOf<Boolean>(false) }


    val value: String = when {
        polarConnected && state.measuring -> {
            // Connected case
            // Your existing logic based on CombinedSensorData
            when (val combinedPolarSensorData = vm.combinedPolarDataFlow.collectAsState().value) {
                is CombinedPolarSensorData.AngleData -> {
                    val angle1pol = combinedPolarSensorData.angle1
                    val angle2pol = combinedPolarSensorData.angle2
                    val time1 = combinedPolarSensorData.time1
                    if (angle1pol == null || angle2pol == null) {
                        "-"
                    } else {
                        String.format(
                            "Angle (algorithm 1) = %.1f, Angle (algorithm 2) = %.1f",
                            angle1pol,
                            angle2pol
                        )
                    }


                }
                else -> "-"
            }
        }

        internalConnected && state.measuring  -> {
            // Display internal sensor data when measuring
            when (val internalSensorData = vm.combinedInternalDataFlow.collectAsState().value) {
                is internalSensorData.InternalAngles -> {
                    val intAngle1 = internalSensorData.intAngle1
                    val intAngle2 = internalSensorData.intAngle2
                    if (intAngle1 == null || intAngle2 == null) {
                        "no angle"
                    } else {
                        String.format(
                            "Internal Angle 1: %.1f\nInternal Angle 2: %.1f",
                            intAngle1,
                            intAngle2,
                        )
                    }
                }
                else -> "-"
            }
        }

    else -> {
            // Not connected case
            // Define the string when not connected
            when (val combinedSensorData = vm.combinedDataFlow.collectAsState().value) {
                is CombinedSensorData.GyroData -> {
                    val triple = combinedSensorData.gyro
                    if (triple == null) {
                        "-"
                    } else {
                        String.format("%.1f, %.1f, %.1f", triple.first, triple.second, triple.third)
                    }
                }
                is CombinedSensorData.HrData -> combinedSensorData.hr.toString()
                is CombinedSensorData.AccelerometerData -> {
                    val accData = combinedSensorData.acc
                    if (accData == null) {
                        "-"
                    } else {
                        val accString = String.format("%.1f, %.1f, %.1f", accData.first, accData.second, accData.third)
                        val angleString = combinedSensorData.ang?.toString()
                        "$accString\nAngle: $angleString"
                    }
                }
                else -> "-"
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = if (state.connected) "Polar sense connected" else "Internal sensors connected")
        Spacer(modifier = Modifier.height(200.dp))



        Text(text = "Select sensor:")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ){
            Button(
                onClick = vm::connectToSensor,
                enabled = !polarConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier
                    .height(60.dp)
                    .width(140.dp)
            ) {
                Text(text = "Polar sense", fontSize = 18.sp)
            }
            Button(
                onClick = vm::disconnectFromSensor,
                enabled =  !internalConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier
                    .height(60.dp)
                    .width(140.dp)
            ) {
                Text(text = "Internal sensors", fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ){
            Button(
                onClick = {
                    if (state.connected) {
                        vm.startPolar()
                    } else {
                        vm.startImuStream()
                    }
                    vm.startRecording()
                    navController.navigate("Graphscreen")
                }, // Navigate to Graphscreen,
                enabled = (!state.measuring),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color.Gray
                ),
                modifier = Modifier
                    .height(80.dp)
                    .width(180.dp)
            ) {
                Text(text = "Start", fontSize = 22.sp)
            }
        }
            // Display the recording in progress text only if recording is in progress
            Text(
                text = if (recordingInProgress) "Recording in progress..." else "",
                fontSize = 16.sp,
                modifier = Modifier.padding(8.dp)
            )
        }

    }









