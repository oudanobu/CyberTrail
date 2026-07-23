package com.cybertrail.app.gis.ins

/**
 * Navigation state machine for CyberTrail Inertial Navigation System (INS / PDR)
 */
enum class NavState {
    /**
     * Normal state: GPS signal precision is good (accuracy <= 15m)
     */
    NORMAL,

    /**
     * Hybrid state: Fusing GPS + IMU sensors for optimal accuracy
     */
    HYBRID,

    /**
     * INS / PDR only state: GPS lost or poor accuracy (> 15m), relying on Dead Reckoning
     */
    INS_ONLY,

    /**
     * GPS recovery state: GPS re-acquired, Kalman filter smoothly correcting accumulated drift
     */
    GPS_RECOVERY
}
