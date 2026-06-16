package com.cybertrail.app.repository

import com.cybertrail.app.model.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TrackingRepository {

    private val _state = MutableStateFlow(TrackingState())

    val state: StateFlow<TrackingState>
        get() = _state

    fun update(newState: TrackingState) {
        _state.value = newState
    }

    fun reset() {
        _state.value = TrackingState()
    }
}
