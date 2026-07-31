package co.elastic.demo.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import co.elastic.demo.android.BuildConfig
import co.elastic.demo.android.ui.screens.CartScreen
import co.elastic.demo.android.ui.screens.CheckoutScreen
import co.elastic.demo.android.ui.screens.ProductDetailScreen
import co.elastic.demo.android.ui.screens.ProductListScreen
import co.elastic.demo.android.viewmodel.ShopViewModel

object Routes {
    const val PRODUCTS = "products"
    const val PRODUCT_DETAIL = "product/{productId}"
    const val CART = "cart"
    const val CHECKOUT = "checkout"

    fun productDetail(productId: String) = "product/$productId"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    // Single ViewModel instance shared across all screens
    val viewModel: ShopViewModel = viewModel(
        factory = ShopViewModel.provideFactory(BuildConfig.FRONTEND_PROXY_URL)
    )

    NavHost(
        navController = navController,
        startDestination = Routes.PRODUCTS
    ) {
        composable(Routes.PRODUCTS) {
            ProductListScreen(
                viewModel = viewModel,
                onProductClick = { productId ->
                    navController.navigate(Routes.productDetail(productId))
                },
                onCartClick = {
                    navController.navigate(Routes.CART)
                }
            )
        }

        composable(
            route = Routes.PRODUCT_DETAIL,
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            ProductDetailScreen(
                viewModel = viewModel,
                productId = productId,
                onBackClick = { navController.popBackStack() },
                onCartClick = { navController.navigate(Routes.CART) }
            )
        }

        composable(Routes.CART) {
            CartScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onCheckoutClick = { navController.navigate(Routes.CHECKOUT) }
            )
        }

        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onOrderSuccess = {
                    // Navigate back to product list and clear the back stack
                    navController.navigate(Routes.PRODUCTS) {
                        popUpTo(Routes.PRODUCTS) { inclusive = true }
                    }
                }
            )
        }
    }
}
