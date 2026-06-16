package com.cybertrail.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.GnssStatus
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var tvGpsStatus: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var tvSatellites: TextView
    private lateinit var btnLaunchMap: Button

    private lateinit var locationManager: LocationManager
    private var gnssStatusCallback: GnssStatus.Callback? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 42
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvGpsStatus = findViewById(R.id.tv_gps_status)
        tvCoordinates = findViewById(R.id.tv_coordinates)
        tvSatellites = findViewById(R.id.tv_satellites)
        btnLaunchMap = findViewById(R.id.btn_launch_map)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        btnLaunchMap.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationUpdates()
            } else {
                tvGpsStatus.text = "GPS状态: 权限未授予 (无定位权限)"
                Toast.makeText(this, "需要定位权限以使用真实GPS定位", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            // Priority: GPS over Network provider
            val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!hasGps && !hasNetwork) {
                tvGpsStatus.text = "GPS状态: 手机定位功能处于关闭状态"
                return
            }

            // Real physical GPS requested (no simulation by default!)
            tvGpsStatus.text = "GPS状态: 正在对接手机物理GPS服务..."
            
            if (hasGps) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    this
                )
            } else {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    this
                )
            }

            // Register GnssStatus callback for satellites tracking
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val callback = object : GnssStatus.Callback() {
                        override fun onSatelliteStatusChanged(status: GnssStatus) {
                            var total = 0
                            var usedInFix = 0
                            for (i in 0 until status.satelliteCount) {
                                total++
                                if (status.usedInFix(i)) {
                                    usedInFix++
                                }
                            }
                            tvSatellites.text = "活跃卫星数: $total 颗 (参与定位: $usedInFix 颗)"
                            if (usedInFix > 0) {
                                tvGpsStatus.text = "GPS状态: 已建立三维卫星锁定 (3D Fixed)"
                            } else {
                                tvGpsStatus.text = "GPS状态: 正在搜星定位中..."
                            }
                        }
                    }
                    locationManager.registerGnssStatusCallback(mainExecutor, callback)
                    gnssStatusCallback = callback
                }
            }

        } catch (e: SecurityException) {
            tvGpsStatus.text = "GPS状态: 权限异常 (${e.message})"
        }
    }

    override fun onLocationChanged(location: Location) {
        val lat = location.latitude
        val lon = location.longitude
        tvCoordinates.text = "当前坐标: 纬度 %.6f°, 经度 %.6f°".format(lat, lon)
        tvGpsStatus.text = "GPS状态: 已使用物理GPS精确搜定方位"
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback!!)
        }
    }
}
