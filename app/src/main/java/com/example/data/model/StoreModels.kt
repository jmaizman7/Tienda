package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

enum class ProductCategory(val displayName: String) {
    ELECTRONICA("Electrónica"),
    HOGAR("Hogar"),
    ACCESORIOS("Accesorios"),
    MODA("Moda"),
    BEBIDAS("Bebidas"),
    OTROS("Otros");

    companion object {
        fun fromRaw(raw: String?): ProductCategory =
            entries.firstOrNull { it.name == raw } ?: OTROS
    }
}

enum class PaymentMethod(val displayName: String) {
    EFECTIVO("Efectivo"),
    TARJETA("Tarjeta"),
    TRANSFERENCIA("Transferencia");

    companion object {
        fun fromRaw(raw: String?): PaymentMethod =
            entries.firstOrNull { it.name == raw } ?: EFECTIVO
    }
}

enum class SaleStatus(val displayName: String) {
    COMPLETADA("Completada"),
    CANCELADA("Cancelada");

    companion object {
        fun fromRaw(raw: String?): SaleStatus =
            entries.firstOrNull { it.name == raw } ?: COMPLETADA
    }
}

@Keep
@IgnoreExtraProperties
data class StoreProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeName: String = "MiTienda",
    val ownerEmail: String = "",
    val catalogSeeded: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = ProductCategory.OTROS.name,
    val priceCents: Long = 0L,
    val stock: Long = 0L,
    val sku: String = "",
    val imageKey: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val categoryEnum: ProductCategory
        get() = ProductCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class SaleItem(
    val productId: String = "",
    val productName: String = "",
    val quantity: Long = 0L,
    val unitPriceCents: Long = 0L,
    val subtotalCents: Long = 0L,
)

@Keep
@IgnoreExtraProperties
data class Sale(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerName: String = "Cliente Mostrador",
    val paymentMethod: String = PaymentMethod.EFECTIVO.name,
    val status: String = SaleStatus.COMPLETADA.name,
    val items: List<SaleItem> = emptyList(),
    val totalCents: Long = 0L,
    val totalItems: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val paymentMethodEnum: PaymentMethod
        get() = PaymentMethod.fromRaw(paymentMethod)

    val statusEnum: SaleStatus
        get() = SaleStatus.fromRaw(status)
}

data class CartItem(
    val product: Product,
    val quantity: Int,
) {
    val subtotalCents: Long
        get() = product.priceCents * quantity.toLong()
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun formatPriceCents(cents: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    return formatter.format(cents / 100.0)
}

fun parsePriceToCents(input: String): Long? {
    val normalized = input.trim().replace(",", ".").replace("$", "").replace(" ", "")
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    if (value <= 0.0 || value > 1_000_000.0) return null
    return (value * 100.0).roundToLong()
}
