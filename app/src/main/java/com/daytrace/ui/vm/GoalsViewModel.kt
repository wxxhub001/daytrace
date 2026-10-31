package com.daytrace.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daytrace.ServiceLocator
import com.daytrace.data.GoalEntity
import com.daytrace.data.GoalProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GoalsUiState(
    val loading: Boolean = true,
    val goals: List<GoalProgress> = emptyList(),
    val doneToday: Int = 0,
    val total: Int = 0,
)

class GoalsViewModel(private val container: ServiceLocator.Container) : ViewModel() {

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            if (_state.value.goals.isEmpty()) _state.value = _state.value.copy(loading = true)
            container.repository.sync()
            publish()
        }
    }

    private suspend fun publish() {
        val list = container.goalRepository.progressList()
        _state.value = GoalsUiState(
            loading = false,
            goals = list,
            doneToday = list.count { it.todayDone },
            total = list.size,
        )
    }

    fun createScreenLimit(title: String, minutes: Int) {
        viewModelScope.launch {
            container.goalRepository.addScreenLimit(title, minutes)
            publish()
        }
    }

    fun createCheckin(title: String, period: String, count: Int) {
        viewModelScope.launch {
            container.goalRepository.addCheckin(title, period, count)
            publish()
        }
    }

    fun createCountdown(title: String, dueDayKey: String) {
        viewModelScope.launch {
            container.goalRepository.addCountdown(title, dueDayKey)
            publish()
        }
    }

    fun toggleCheckin(goalId: Long) {
        viewModelScope.launch {
            container.goalRepository.toggleCheckin(goalId)
            publish()
        }
    }

    fun setArchived(goal: GoalEntity, archived: Boolean) {
        viewModelScope.launch {
            container.goalRepository.setArchived(goal, archived)
            publish()
        }
    }

    fun remove(goalId: Long) {
        viewModelScope.launch {
            container.goalRepository.remove(goalId)
            publish()
        }
    }

    companion object {
        fun factory(app: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { GoalsViewModel(ServiceLocator.get(app)) }
        }
    }
}