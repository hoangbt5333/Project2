package com.example.project2.data.firebase

import com.example.project2.domain.model.ControlState
import com.example.project2.domain.model.SensorData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirebaseRepository(
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
) {
    private val rootRef = database.getReference(FirebasePaths.ROOT)
    private val controlRef = database.getReference(FirebasePaths.CONTROL)

    fun observeSensorData(): Flow<SensorData> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = SensorData(
                    airTemperature = snapshot.doubleValue("air_temperature", 0.0),
                    airHumidity = snapshot.doubleValue("air_humidity", 0.0),
                    soilMoisture = snapshot.intValue("soil_moisture", 0),
                    npkN = snapshot.intValue("npk_n", 0),
                    npkP = snapshot.intValue("npk_p", 0),
                    npkK = snapshot.intValue("npk_k", 0),
                    timestamp = System.currentTimeMillis()
                )

                trySend(data)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        rootRef.addValueEventListener(listener)

        awaitClose {
            rootRef.removeEventListener(listener)
        }
    }

    fun observeControlState(): Flow<ControlState> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = ControlState(
                    autoMode = snapshot.booleanValue("auto_mode", true),
                    pump = snapshot.booleanValue("pump", false),
                    fan = snapshot.booleanValue("fan", false),
                    soilThreshold = snapshot.intValue("soil_threshold", 40).coerceIn(0, 100),
                    tempThreshold = snapshot.doubleValue("temp_threshold", 35.0).coerceIn(0.0, 60.0)
                )

                trySend(state)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        controlRef.addValueEventListener(listener)

        awaitClose {
            controlRef.removeEventListener(listener)
        }
    }

    fun setAutoMode(enabled: Boolean) {
        controlRef.child("auto_mode").setValue(enabled)
    }

    fun setPump(enabled: Boolean) {
        controlRef.child("pump").setValue(enabled)
    }

    fun setFan(enabled: Boolean) {
        controlRef.child("fan").setValue(enabled)
    }

    fun setSoilThreshold(value: Int) {
        controlRef.child("soil_threshold").setValue(value.coerceIn(0, 100))
    }

    fun setTempThreshold(value: Double) {
        controlRef.child("temp_threshold").setValue(value.coerceIn(0.0, 60.0))
    }

    private fun DataSnapshot.childByPath(path: String): DataSnapshot {
        return path.split("/").fold(this) { current, key -> current.child(key) }
    }

    private fun DataSnapshot.intValue(path: String, default: Int): Int {
        val child = childByPath(path)

        return child.getValue(Int::class.java)
            ?: child.getValue(Long::class.java)?.toInt()
            ?: child.getValue(Double::class.java)?.toInt()
            ?: default
    }

    private fun DataSnapshot.doubleValue(path: String, default: Double): Double {
        val child = childByPath(path)

        return child.getValue(Double::class.java)
            ?: child.getValue(Long::class.java)?.toDouble()
            ?: child.getValue(Int::class.java)?.toDouble()
            ?: default
    }

    private fun DataSnapshot.booleanValue(path: String, default: Boolean): Boolean {
        return childByPath(path).getValue(Boolean::class.java) ?: default
    }
}