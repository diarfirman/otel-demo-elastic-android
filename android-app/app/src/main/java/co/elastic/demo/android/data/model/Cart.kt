package co.elastic.demo.android.data.model

/**
 * Enriched cart response from GET /api/cart — each item includes the full product details.
 */
data class CartResponse(
    val userId: String,
    val items: List<CartItemWithProduct>
)

/**
 * A cart item enriched with product details, as returned by the Next.js cart API.
 */
data class CartItemWithProduct(
    val productId: String,
    val quantity: Int,
    val product: Product
)

/**
 * Request body for POST /api/cart (add item).
 */
data class AddToCartRequest(
    val userId: String,
    val item: CartItemRequest
)

/**
 * Item portion of the add-to-cart request body.
 */
data class CartItemRequest(
    val productId: String,
    val quantity: Int
)

/**
 * Request body for DELETE /api/cart (empty cart).
 */
data class EmptyCartRequest(
    val userId: String
)
