package co.elastic.demo.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.elastic.demo.android.BuildConfig
import co.elastic.demo.android.data.api.ApiClient
import co.elastic.demo.android.data.model.CartItemWithProduct
import co.elastic.demo.android.data.model.CartResponse
import co.elastic.demo.android.data.model.CheckoutRequest
import co.elastic.demo.android.data.model.Product
import co.elastic.demo.android.data.repository.ShopRepository
import io.opentelemetry.api.GlobalOpenTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ShopUiState(
    val products: List<Product> = emptyList(),
    val isLoadingProducts: Boolean = false,
    val cart: CartResponse = CartResponse(userId = "", items = emptyList()),
    val isLoadingCart: Boolean = false,
    val currencies: List<String> = listOf("USD"),
    val isProcessingCheckout: Boolean = false,
    val orderId: String? = null,
    val error: String? = null,
    val snackbarMessage: String? = null
)

class ShopViewModel(private val repository: ShopRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    /**
     * Session ID that persists for the lifetime of this ViewModel.
     * Used as the cart's user ID so the cart state is consistent across screens.
     */
    val sessionId: String = UUID.randomUUID().toString()

    private val tracer = GlobalOpenTelemetry.getTracer("otel-demo-android")

    init {
        loadProducts()
        loadCart()
        loadCurrencies()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProducts = true, error = null) }
            repository.getProducts()
                .onSuccess { products ->
                    _uiState.update { it.copy(products = products, isLoadingProducts = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingProducts = false,
                            error = "Failed to load products: ${error.message}"
                        )
                    }
                }
        }
    }

    fun loadCart() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCart = true) }
            repository.getCart(sessionId)
                .onSuccess { cart ->
                    _uiState.update { it.copy(cart = cart, isLoadingCart = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingCart = false,
                            error = "Failed to load cart: ${error.message}"
                        )
                    }
                }
        }
    }

    private fun loadCurrencies() {
        viewModelScope.launch {
            repository.getCurrencies()
                .onSuccess { currencies ->
                    _uiState.update { it.copy(currencies = currencies) }
                }
        }
    }

    fun addToCart(productId: String, quantity: Int = 1) {
        viewModelScope.launch {
            val productName = _uiState.value.products.find { it.id == productId }?.name ?: productId
            val span = tracer.spanBuilder("user.add_to_cart")
                .setAttribute("product.id", productId)
                .setAttribute("product.name", productName)
                .setAttribute("cart.session_id", sessionId)
                .setAttribute("cart.quantity", quantity.toLong())
                .startSpan()
            val scope = span.makeCurrent()
            try {
                repository.addToCart(sessionId, productId, quantity)
                    .onSuccess { cart ->
                        _uiState.update {
                            it.copy(
                                cart = cart,
                                snackbarMessage = "Added to cart!"
                            )
                        }
                        span.setAttribute("outcome", "success")
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(error = "Failed to add item to cart: ${error.message}")
                        }
                        span.setAttribute("outcome", "failure")
                        span.recordException(error)
                    }
            } finally {
                scope.close()
                span.end()
            }
        }
    }

    fun removeFromCart(productId: String) {
        val currentItems = _uiState.value.cart.items
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCart = true) }
            repository.removeCartItem(sessionId, productId, currentItems)
                .onSuccess { cart ->
                    _uiState.update { it.copy(cart = cart, isLoadingCart = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingCart = false,
                            error = "Failed to remove item: ${error.message}"
                        )
                    }
                }
        }
    }

    fun updateCartItemQuantity(productId: String, newQuantity: Int) {
        val currentItems = _uiState.value.cart.items
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCart = true) }
            repository.updateCartItemQuantity(sessionId, productId, newQuantity, currentItems)
                .onSuccess { cart ->
                    _uiState.update { it.copy(cart = cart, isLoadingCart = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingCart = false,
                            error = "Failed to update cart: ${error.message}"
                        )
                    }
                }
        }
    }

    fun recordViewProduct(productId: String) {
        val productName = _uiState.value.products.find { it.id == productId }?.name ?: productId
        val span = tracer.spanBuilder("user.view_product")
            .setAttribute("product.id", productId)
            .setAttribute("product.name", productName)
            .setAttribute("cart.session_id", sessionId)
            .startSpan()
        span.end()
    }

    fun recordBeginCheckout() {
        val span = tracer.spanBuilder("user.begin_checkout")
            .setAttribute("cart.session_id", sessionId)
            .setAttribute("cart.item_count", _uiState.value.cart.items.size.toLong())
            .startSpan()
        span.end()
    }

    fun placeOrder(checkoutRequest: CheckoutRequest) {
        viewModelScope.launch {
            val span = tracer.spanBuilder("user.place_order")
                .setAttribute("cart.session_id", sessionId)
                .setAttribute("order.currency", checkoutRequest.userCurrency)
                .startSpan()
            val scope = span.makeCurrent()
            try {
                _uiState.update { it.copy(isProcessingCheckout = true, error = null) }
                repository.checkout(sessionId, checkoutRequest)
                    .onSuccess { response ->
                        val orderId = response.orderId ?: "unknown"
                        span.setAttribute("order.id", orderId)
                        span.setAttribute("outcome", "success")
                        repository.emptyCart(sessionId)
                        _uiState.update {
                            it.copy(
                                isProcessingCheckout = false,
                                orderId = orderId,
                                cart = CartResponse(userId = sessionId, items = emptyList())
                            )
                        }
                    }
                    .onFailure { error ->
                        span.setAttribute("outcome", "failure")
                        span.recordException(error)
                        _uiState.update {
                            it.copy(
                                isProcessingCheckout = false,
                                error = "Failed to place order: ${error.message}"
                            )
                        }
                    }
            } finally {
                scope.close()
                span.end()
            }
        }
    }

    fun dismissOrderResult() {
        _uiState.update { it.copy(orderId = null) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun provideFactory(baseUrl: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val api = ApiClient.create(baseUrl)
                    val repository = ShopRepository(api)
                    return ShopViewModel(repository) as T
                }
            }
        }
    }
}
