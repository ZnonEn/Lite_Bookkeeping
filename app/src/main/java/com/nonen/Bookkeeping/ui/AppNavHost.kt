package com.nonen.Bookkeeping.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.ui.screens.AddEditScreen
import com.nonen.Bookkeeping.ui.screens.AddEditViewModel
import com.nonen.Bookkeeping.ui.screens.HomeScreen
import com.nonen.Bookkeeping.ui.screens.HomeViewModel
import com.nonen.Bookkeeping.ui.screens.RulesScreen
import com.nonen.Bookkeeping.ui.screens.RulesViewModel
import com.nonen.Bookkeeping.ui.screens.SearchScreen
import com.nonen.Bookkeeping.ui.screens.SearchViewModel
import com.nonen.Bookkeeping.ui.screens.SettingsScreen
import com.nonen.Bookkeeping.ui.screens.SettingsViewModel
import com.nonen.Bookkeeping.ui.screens.StatsViewModel
import com.nonen.Bookkeeping.ui.screens.StatisticsScreen

object Routes {
    const val HOME = "home"
    const val ADD_EDIT = "add/{txId}"
    const val SEARCH = "search"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val RULES = "rules"

    fun add(txId: Long) = "add/$txId"
}

inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { create() }
    }

@Composable
fun AppNavHost() {
    val container = (LocalContext.current.applicationContext as BookkeepingApp).container
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(container.transactionRepository) })
            HomeScreen(
                vm = vm,
                onAdd = { nav.navigate(Routes.add(0L)) },
                onEdit = { nav.navigate(Routes.add(it)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onStats = { nav.navigate(Routes.STATS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.STATS) {
            val vm: StatsViewModel = viewModel(factory = vmFactory { StatsViewModel(container.transactionRepository) })
            StatisticsScreen(vm = vm)
        }

        composable(
            Routes.ADD_EDIT,
            arguments = listOf(navArgument("txId") { type = NavType.LongType }),
        ) { entry ->
            val txId = entry.arguments?.getLong("txId") ?: 0L
            val vm: AddEditViewModel = viewModel(
                key = "add_edit_$txId",
                factory = vmFactory { AddEditViewModel(container.transactionRepository, txId) },
            )
            AddEditScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.SEARCH) {
            val vm: SearchViewModel = viewModel(factory = vmFactory { SearchViewModel(container.transactionRepository) })
            SearchScreen(vm = vm, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.add(it)) })
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = vmFactory { SettingsViewModel(container) })
            SettingsScreen(vm = vm, onRules = { nav.navigate(Routes.RULES) }, onBack = { nav.popBackStack() })
        }

        composable(Routes.RULES) {
            val vm: RulesViewModel = viewModel(factory = vmFactory { RulesViewModel(container.ruleRepository) })
            RulesScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
