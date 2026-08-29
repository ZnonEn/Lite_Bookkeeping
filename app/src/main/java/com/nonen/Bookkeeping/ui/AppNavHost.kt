package com.nonen.Bookkeeping.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nonen.Bookkeeping.BookkeepingApp
import com.nonen.Bookkeeping.ui.motion.motionSpring
import com.nonen.Bookkeeping.ui.motion.rememberReducedMotion
import com.nonen.Bookkeeping.ui.screens.AddEditScreen
import com.nonen.Bookkeeping.ui.screens.AddEditViewModel
import com.nonen.Bookkeeping.ui.screens.MainScreen
import com.nonen.Bookkeeping.ui.screens.RulesScreen
import com.nonen.Bookkeeping.ui.screens.RulesViewModel
import com.nonen.Bookkeeping.ui.screens.SearchScreen
import com.nonen.Bookkeeping.ui.screens.SearchViewModel

object Routes {
    const val MAIN = "main"
    const val ADD_EDIT = "add/{txId}"
    const val SEARCH = "search"
    const val RULES = "rules"

    fun add(txId: Long) = "add/$txId"
}

inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { create() }
    }

/** iOS 风格推入/推出过渡：新页整宽从右滑入，旧页退到 -1/3 宽；弹出相反（参照墨麒麟） */
@Composable
fun AppNavHost() {
    val container = (LocalContext.current.applicationContext as BookkeepingApp).container
    val nav = rememberNavController()
    val reduced = rememberReducedMotion()

    NavHost(
        navController = nav,
        startDestination = Routes.MAIN,
        // 滑动页面保持完全不透明（iOS 推入式）：任何时刻进出两页都铺满整屏，
        // 半透明淡入/淡出会让底层窗口背景透出造成白闪
        enterTransition = {
            if (reduced) fadeIn(tween(150))
            else slideInHorizontally(motionSpring()) { it }
        },
        exitTransition = {
            if (reduced) fadeOut(tween(150))
            else slideOutHorizontally(motionSpring()) { -it / 3 }
        },
        popEnterTransition = {
            if (reduced) fadeIn(tween(150))
            else slideInHorizontally(motionSpring()) { -it / 3 }
        },
        popExitTransition = {
            if (reduced) fadeOut(tween(150))
            else slideOutHorizontally(motionSpring()) { it }
        },
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                container = container,
                onAdd = { nav.navigate(Routes.add(0L)) },
                onEdit = { nav.navigate(Routes.add(it)) },
                onSearch = { nav.navigateSingleTop(Routes.SEARCH) },
                onRules = { nav.navigateSingleTop(Routes.RULES) },
            )
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

        composable(Routes.RULES) {
            val vm: RulesViewModel = viewModel(factory = vmFactory { RulesViewModel(container.ruleRepository) })
            RulesScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

/** 相同目的地不重复入栈，避免反复点击搜索/规则堆出多层页面 */
private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
