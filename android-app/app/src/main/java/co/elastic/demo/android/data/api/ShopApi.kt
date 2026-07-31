package co.elastic.demo.android.data.api

import co.elastic.demo.android.data.model.AddToCartRequest
import co.elastic.demo.android.data.model.CartResponse
import co.elastic.demo.android.data.model.CheckoutRequest
import co.elastic.demo.android.data.model.CheckoutResponse
import co.elastic.demo.android.data.model.EmptyCartRequest
import co.elastic.demo.android.data.model.Product
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ShopApi {

    /**
     * List all products. Optionally pass a currency code for price conversion.
     */
    @GET("api/products")
    suspend fun getProducts(
        @Query("currencyCode") currencyCode: String = "USD"
    ): List<Product>

    /**
     * Get a single product by ID.
     */
    @GET("api/products/{productId}")
    suspend fun getProduct(
        @Path("productId") productId: String,
        @Query("currencyCode") currencyCode: String = "USD"
    ): Product

    /**
     * Get the enriched cart for a session. Each item includes full product details.
     */
    @GET("api/cart")
    suspend fun getCart(
        @Query("sessionId") sessionId: String,
        @Query("currencyCode") currencyCode: String = "USD"
    ): CartResponse

    /**
     * Add an item to the cart (or update its quantity by replacing it).
     * The backend adds the quantity on top of any existing quantity for the same product.
     */
    @POST("api/cart")
    suspend fun addToCart(
        @Body request: AddToCartRequest
    ): CartResponse

    /**
     * Empty the entire cart for the given user.
     */
    @DELETE("api/cart")
    suspend fun emptyCart(
        @Body request: EmptyCartRequest
    ): Response<Unit>

    /**
     * Place an order. currencyCode is passed as a query parameter.
     */
    @POST("api/checkout")
    suspend fun checkout(
        @Query("currencyCode") currencyCode: String,
        @Body request: CheckoutRequest
    ): CheckoutResponse

    /**
     * Get supported currency codes.
     */
    @GET("api/currency")
    suspend fun getCurrencies(): List<String>

    /**
     * Get product recommendations.
     */
    @GET("api/recommendations")
    suspend fun getRecommendations(
        @Query("productIds") productIds: List<String>,
        @Query("sessionId") sessionId: String,
        @Query("currencyCode") currencyCode: String = "USD"
    ): List<Product>
}
