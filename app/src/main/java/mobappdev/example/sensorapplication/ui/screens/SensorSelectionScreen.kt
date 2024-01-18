package mobappdev.example.sensorapplication.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import mobappdev.example.sensorapplication.domain.BluetoothDeviceDomain
import mobappdev.example.sensorapplication.ui.viewmodels.DataVM

@Composable
fun SensorSelectionScreen(
    vm: DataVM,
    navController: NavHostController
) {

    val state = vm.state.collectAsStateWithLifecycle().value
    val deviceId = vm.deviceId.collectAsStateWithLifecycle().value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Select a Polar Sensor:")
        Spacer(modifier = Modifier.height(16.dp))

        // Display the list of sensors
        LazyColumn {

            item {
                Text(
                    text = "Scanned devices",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(50.dp))

            }

            items(state.scannedDevices) { device ->
                if (device.name != null && device.name.contains("Polar")) {

                    Text(text = device.name,
                        modifier = Modifier.clickable {
                            (vm::chooseSensor)(device.name.substring(12, 20));
                            //vm.connectToSensor()
                            navController.navigate("Graphscreen")
                        })
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))

        // Back button to navigate back
        Button(
            onClick = {
               // onNavigateBack()
                navController.popBackStack()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth()
        ) {
            Text(text = "Back", fontSize = 18.sp)
        }
    }
}
