package co.elastic.demo.android.data.model

/**
 * Request body for POST /api/checkout.
 * Field names match the protobuf PlaceOrderRequest used by the demo backend.
 */
data class CheckoutRequest(
    val userId: String,
    val email: String,
    val address: Address,
    val userCurrency: String = "USD",
    val creditCard: CreditCard = CreditCard()
)

data class Address(
    val streetAddress: String,
    val city: String,
    val state: String,
    val country: String,
    val zipCode: String
)

/**
 * Pre-filled demo credit card — not editable by the user.
 * Field names match the protobuf CreditCardInfo.
 */
data class CreditCard(
    val creditCardNumber: String = "4432801561520454",
    val creditCardCvv: Int = 672,
    val creditCardExpirationYear: Int = 2030,
    val creditCardExpirationMonth: Int = 1
)

/**
 * Minimal representation of the checkout response.
 * The backend returns the full OrderResult; we only surface orderId.
 */
data class CheckoutResponse(
    val orderId: String? = null,
    val shippingTrackingId: String? = null
)
