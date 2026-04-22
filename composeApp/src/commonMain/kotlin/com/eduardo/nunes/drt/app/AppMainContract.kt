package com.eduardo.nunes.drt.app

interface AppMainContract {

    data class State(
        val showNavigationRail: Boolean = false,
        val menuItems: List<NavRailItem> = NavRailItem.entries
    )

    sealed class Intent {
        data class NavigateTo(val route: String) : Intent()
        object OpenCloseNavRail : Intent()
        object NavigateBack : Intent()
        object ClearLogs : Intent()
    }

    sealed class Effect {
        data class NavigateTo(val route: String) : Effect()
        object NavigateBack : Effect()
    }
}
