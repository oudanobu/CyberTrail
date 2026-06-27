package com.cybertrail.app.db

class WaypointRepository(private val waypointDao: WaypointDao) {
    fun insert(waypoint: WaypointEntity) {
        waypointDao.insertWaypoint(waypoint)
    }

    fun update(waypoint: WaypointEntity) {
        waypointDao.updateWaypoint(waypoint)
    }

    fun getById(id: String): WaypointEntity? {
        return waypointDao.getWaypointById(id)
    }

    fun getAll(): List<WaypointEntity> {
        return waypointDao.getAllWaypoints()
    }

    fun delete(id: String) {
        waypointDao.deleteWaypointById(id)
    }
}
