package com.cybertrail.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

object PermissionHelper {

    val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    fun hasPermissions(context: Context): Boolean {
        for (permission in permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
}
