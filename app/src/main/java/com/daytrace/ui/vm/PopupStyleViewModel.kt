package com.daytrace.ui.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daytrace.ServiceLocator
import com.daytrace.data.PopupBgMode
import com.daytrace.data.PopupStyle
import com.daytrace.system.PopupBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PopupStyleUiState(
    val loading: Boolean = true,
    val style: PopupStyle = PopupStyle(),
    val globalStyle: PopupStyle = PopupStyle(),
    /** 是否在编辑"某一条规则"的样式 */
    val perApp: Boolean = false,
    val hasOverride: Boolean = false,
    val saved: Boolean = false,
)

/**
 * 弹窗样式编辑。[packageName] 为 null 时编辑全局样式，
 * 否则编辑这条规则的独立样式（没有独立样式时从全局样式起步）。
 */
class PopupStyleViewModel(
    private val container: ServiceLocator.Container,
    private val packageName: String?,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(PopupStyleUiState(perApp = packageName != null))
    val state: StateFlow<PopupStyleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            val perApp = packageName != null
            _state.value = PopupStyleUiState(
                loading = false,
                style = settings.styleFor(packageName),
                globalStyle = settings.popupStyle,
                perApp = perApp,
                hasOverride = settings.hasPopupOverride(packageName),
            )
        }
    }

    fun update(transform: (PopupStyle) -> PopupStyle) {
        val current = _state.value
        _state.value = current.copy(style = transform(current.style), saved = false)
    }

    /** 选了一张背景图：先落盘再更新样式 */
    fun attachImage(localPath: String) {
        update { it.copy(bgMode = PopupBgMode.IMAGE, bgImagePath = localPath) }
    }

    fun clearImage() {
        PopupBackground.removeSaved(appContext)
        update { it.copy(bgMode = PopupBgMode.COLOR, bgImagePath = null) }
    }

    fun save() {
        viewModelScope.launch {
            val current = _state.value
            when {
                current.perApp && packageName != null ->
                    container.settings.setPopupStyleOverride(packageName, current.style)

                else -> container.settings.setPopupStyle(current.style)
            }
            _state.value = current.copy(saved = true, hasOverride = current.perApp)
        }
    }

    /** 单条规则改回跟随全局 */
    fun resetToGlobal() {
        val pkg = packageName ?: return
        viewModelScope.launch {
            container.settings.setPopupStyleOverride(pkg, null)
            _state.value = _state.value.copy(
                style = _state.value.globalStyle,
                hasOverride = false,
                saved = true,
            )
        }
    }

    fun resetToFactoryDefault() {
        update { PopupStyle() }
    }

    companion object {
        fun factory(app: Context, packageName: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer { PopupStyleViewModel(ServiceLocator.get(app), packageName, app) }
        }
    }
}