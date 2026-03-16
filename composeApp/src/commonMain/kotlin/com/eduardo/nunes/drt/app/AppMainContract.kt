package com.eduardo.nunes.drt.app

interface AppMainContract {
    sealed class Intent {
        data class NavigateTo(val route: String) : Intent()
        object NavigateBack : Intent()
        object ClearLogs : Intent()
    }

    sealed class Effect {
        data class NavigateTo(val route: String) : Effect()
        object NavigateBack : Effect()
    }
}
