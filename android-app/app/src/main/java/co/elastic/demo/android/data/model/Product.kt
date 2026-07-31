package co.elastic.demo.android.data.model

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val picture: String,
    val priceUsd: Money,
    val categories: List<String>
)

data class Money(
    val currencyCode: String,
    val units: Long,
    val nanos: Int
) {
    fun toDisplayString(): String {
        val cents = nanos / 10_000_000
        return "$currencyCode ${units}.${cents.toString().padStart(2, '0')}"
    }

    fun toDouble(): Double = units + nanos / 1_000_000_000.0
}
