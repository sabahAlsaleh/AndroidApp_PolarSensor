package mobappdev.example.sensorapplication.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import mobappdev.example.sensorapplication.domain.BluetoothDeviceDomain

@SuppressLint("MissingPermission")
fun BluetoothDevice.toBluetoothDeviceDomain(): BluetoothDeviceDomain{
    return BluetoothDeviceDomain(
        name = name,
        address = address
    )
}