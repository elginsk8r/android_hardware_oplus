/*
 * Copyright (C) 2021-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.AmbientDisplayConfiguration
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager

object Utils {
    private const val TAG = "DozeUtils"

    private const val DOZE_INTENT = "com.android.systemui.doze.pulse"

    const val CATEGORY_PICKUP_SENSOR = "pickup_sensor"
    const val CATEGORY_PROXIMITY_SENSOR = "proximity_sensor"

    const val GESTURE_PICK_UP_KEY = "gesture_pick_up_type"
    const val GESTURE_POCKET_KEY = "gesture_pocket"

    private fun startService(context: Context) {
        Log.d(TAG, "Starting service")
        context.startServiceAsUser(Intent(context, DozeService::class.java), UserHandle.CURRENT)
    }

    private fun stopService(context: Context) {
        Log.d(TAG, "Stopping service")
        context.stopServiceAsUser(Intent(context, DozeService::class.java), UserHandle.CURRENT)
    }

    fun checkDozeService(context: Context) {
        if (isDozeEnabled(context) && !isAlwaysOnEnabled(context) && areGesturesEnabled(context)) {
            startService(context)
        } else {
            stopService(context)
        }
    }

    fun isDozeEnabled(context: Context): Boolean {
        return AmbientDisplayConfiguration(context).pulseOnNotificationEnabled(UserHandle.USER_CURRENT)
    }

    fun launchDozePulse(context: Context) {
        Log.d(TAG, "Launch doze pulse")
        context.sendBroadcastAsUser(Intent(DOZE_INTENT), UserHandle(UserHandle.USER_CURRENT))
    }

    fun isAlwaysOnEnabled(context: Context): Boolean {
        return AmbientDisplayConfiguration(context).alwaysOnEnabled(UserHandle.USER_CURRENT)
    }

    fun alwaysOnDisplayAvailable(context: Context): Boolean {
        return AmbientDisplayConfiguration(context).alwaysOnAvailable()
    }

    private fun isGestureEnabled(context: Context, gesture: String?): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(gesture, false)
    }

    fun isPickUpEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(GESTURE_PICK_UP_KEY, "0") != "0"
    }

    fun isPickUpSetToWake(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(GESTURE_PICK_UP_KEY, "0") == "2"
    }

    fun isPocketEnabled(context: Context): Boolean {
        return isGestureEnabled(context, GESTURE_POCKET_KEY)
    }

    private fun areGesturesEnabled(context: Context): Boolean {
        return isPickUpEnabled(context) || isPocketEnabled(context)
    }

    fun getSensor(sm: SensorManager, type: String?): Sensor? {
        return sm.getSensorList(Sensor.TYPE_ALL).find { it.stringType == type }
    }
}
