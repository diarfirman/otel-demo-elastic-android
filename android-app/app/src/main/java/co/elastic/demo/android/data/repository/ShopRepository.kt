package co.elastic.demo.android.data.repository

import co.elastic.demo.android.data.api.ShopApi
import co.elastic.demo.android.data.model.AddToCartRequest
import co.elastic.demo.android.data.model.CartItemRequest
import co.elastic.demo.android.data.model.CartItemWithProduct
import co.elastic.demo.android.data.model.CartResponse
import co.elastic.demo.android.data.model.CheckoutRequest
import co.elastic.demo.android.data.model.CheckoutResponse
import co.elastic.demo.android.data.model.EmptyCartRequest
import co.elastic.demo.android.data.model.Product

class ShopRepository(private val api: ShopApi) {

    suspend fun getProducts(): Result<List<Product>> = runCatching {
        api.getProducts()
    }

    suspend fun getProduct(productId: String): Result<Product> = runCatching {
        api.getProduct(productId)
    }

    suspend fun getCart(sessionId: String): Result<CartResponse> = runCatching {
        api.getCart(sessionId)
    }

    suspend fun addToCart(
        sessionId: String,
        productId: String,
        quantity: Int
    ): Result<CartResponse> = runCatching {
        api.addToCart(
            AddToCartRequest(
                userId = sessionId,
                item = CartItemRequest(productId = productId, quantity = quantity)
            )
        )
        api.getCart(sessionId)
    }

    suspend fun emptyCart(sessionId: String): Result<Unit> = runCatching {
        api.emptyCart(EmptyCartRequest(userId = sessionId))
        Unit
    }

    /**
     * Remove a single item from the cart by emptying the cart and re-adding all other items.
     * This is necessary because the demo API only supports emptying the entire cart.
     */
    suspend fun removeCartItem(
        sessionId: String,
        productIdToRemove: String,
        currentItems: List<CartItemWithProduct>
    ): Result<CartResponse> = runCatching {
        api.emptyCart(EmptyCartRequest(userId = sessionId))
        val remaining = currentItems.filter { it.productId != productIdToRemove }
        for (item in remaining) {
            api.addToCart(
                AddToCartRequest(
                    userId = sessionId,
                    item = CartItemRequest(productId = item.productId, quantity = item.quantity)
                )
            )
        }
        api.getCart(sessionId)
    }

    /**
     * Update the quantity of a specific cart item by emptying and re-adding all items
     * with the updated quantity. This is necessary because the AddItem RPC accumulates quantities.
     */
    suspend fun updateCartItemQuantity(
        sessionId: String,
        productIdToUpdate: String,
        newQuantity: Int,
        currentItems: List<CartItemWithProduct>
    ): Result<CartResponse> = runCatching {
        if (newQuantity <= 0) {
            return removeCartItem(sessionId, productIdToUpdate, currentItems)
        }
        api.emptyCart(EmptyCartRequest(userId = sessionId))
        for (item in currentItems) {
            val qty = if (item.productId == productIdToUpdate) newQuantity else item.quantity
            api.addToCart(
                AddToCartRequest(
                    userId = sessionId,
                    item = CartItemRequest(productId = item.productId, quantity = qty)
                )
            )
        }
        api.getCart(sessionId)
    }

    suspend fun checkout(
        sessionId: String,
        request: CheckoutRequest
    ): Result<CheckoutResponse> = runCatching {
        api.checkout(currencyCode = request.userCurrency, request = request.copy(userId = sessionId))
    }

    suspend fun getCurrencies(): Result<List<String>> = runCatching {
        api.getCurrencies()
    }

    suspend fun getRecommendations(
        productIds: List<String>,
        sessionId: String
    ): Result<List<Product>> = runCatching {
        api.getRecommendations(productIds = productIds, sessionId = sessionId)
    }
}
