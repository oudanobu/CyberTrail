package com.cybertrail.app.db

import androidx.room.*

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRoute(route: RouteEntity)

    @Update
    fun updateRoute(route: RouteEntity)

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    fun getRouteById(id: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY createTime DESC")
    fun getAllRoutes(): List<RouteEntity>

    @Query("DELETE FROM routes WHERE id = :id")
    fun deleteRouteById(id: String)
}
