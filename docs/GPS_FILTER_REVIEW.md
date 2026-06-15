# CyberTrail GPS Filter Review
Version: 1.0
Status: Frozen

---

## 1. Accuracy Gate

The accuracy gate is the first line of defense against corrupted location data. Android's `Location.getAccuracy()` provides the estimated horizontal accuracy radius in meters (68% confidence).

**Thresholds:**
- **Optimal (Clear Sky):** < 10m
- **Acceptable (Forest/Valley):** 10m - 25m
- **Marginal (Deep Canyon/Urban):** 25m - 50m
- **Rejected:** > 50m

**Decision:** The hard rejection threshold is strictly set to **50 meters**.
*Why?* Rejecting points > 25m might cause total tracking failure in deep forests or canyons where users actually need the app. We accept points up to 50m accuracy but apply a stronger weight on the Kalman Filter's state prediction rather than the raw measurement for these low-accuracy points. 

---

## 2. Stationary Detection (Pause/Resting Logic)

When a user stops moving (resting, camping, taking photos), device GPS chips continue to emit coordinates. Due to atmospheric interference and multipath rendering, these points "wander," creating a "bird's nest" of fake mileage and fake elevation gain on the track.

**Stationary Rule:**
- **Spatial Gate:** A new point is only recorded if the distance from the last *recorded* point is $\ge$ **5 meters**.
- **Resting Watchdog:** If the 5-meter threshold is not met within **30 seconds**, the system identifies a "Stationary Mode".
- **Action:** During Stationary Mode, the app **does not** append new coordinates. It injects a "Heartbeat" point every 60 seconds. The heartbeat uses the exact same coordinate and altitude as the last valid point but updates the timestamp.
*Why?* This prevents accumulating ghost distances and ghost climbs, preserves battery life, keeps the UI dashboard live, and prevents the creation of thousands of redundant points in the SQLite database during a 1-hour lunch break.

---

## 3. Spike Rejection (Multipath & Teleportation)

Sometimes a device briefly hallucinates a position kilometers away before snapping back.

**The Speed Check (Teleportation Gate):**
- Calculate the implied speed between the last valid point and the incoming point.
- **`implied_speed = distance_m / (current_time_s - last_time_s)`**
- **Threshold:** If the implied sustained speed is $> 100$ km/h (for walking/hiking/cycling), the point is flagged as a physiological anomaly.

**The Ray-Cast Outlier Strategy:**
1. If Point $N$ implies an erratic speed jump, we place Point $N$ into a `Suspicious Buffer`.
2. We wait for Point $N+1$.
3. If Point $N+1$ snaps back to the original trajectory of $N-1$, Point $N$ was a severe multipath reflection and is **discarded**.
4. If Point $N+1$ confirms the trajectory of Point $N$ (i.e. the user really did jump into a car and drive away), the `Suspicious Buffer` is validated and flushed to memory.

---

## 4. Kalman Filter Tuning

The Kalman Filter tracks a dynamic state matrix: `[latitude, longitude, velocity_lat, velocity_lon]`.
- **Measurement Noise Covariance ($R$):** Dynamically bound to the Android `getAccuracy()` measurement. A high GPS accuracy value increases $R$, making the filter trust its internal physics model (momentum/velocity) more than the erratic new measurement.
- **Process Noise Covariance ($Q$):** Tuned for human walking / trail running. $Q$ is relatively low. (Humans don't accelerate at 9G). 

**Conclusion:**
With a 50m Accuracy Gate, strict 5m Spatial Gated Heartbeats, Speed Gap Anomaly Buffering, and a dynamic Kalman Filter, CyberTrail guarantees pristine track topology, preventing battery drain and eliminating the "drunk walker" trajectory typical of poorly written GPS loggers.
