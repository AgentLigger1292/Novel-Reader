package com.novelreader.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Minimal ViewModel factory for manual DI — every ViewModel takes the
 * AppContainer as its single constructor argument.
 */
class AppVmFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val ctor = modelClass.constructors.firstOrNull { it.parameterTypes.size == 1 }
            ?: error("ViewModel ${modelClass.name} must have a single AppContainer constructor")
        @Suppress("UNCHECKED_CAST")
        return ctor.newInstance(container) as T
    }
}
