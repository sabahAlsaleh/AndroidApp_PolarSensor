package mobappdev.example.sensorapplication.domain

typealias BluetoothDeviceDomain = BluetoothDevice

data class BluetoothDevice(
    val name: String?,
    val address: String
)