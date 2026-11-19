package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.Sale
import com.example.data.model.StoreProfile
import com.example.data.model.UiState
import com.example.data.repository.StoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InventoryFilter(val label: String) {
    ALL("Todos"),
    ACTIVE("Activos"),
    LOW_STOCK("Stock bajo"),
    OUT_OF_STOCK("Agotados"),
    PAUSED("Pausados"),
}

class StoreViewModel(
    private val repository: StoreRepository,
    private val currentUserId: String,
    private val ownerEmail: String?,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    private val _inventoryFilter = MutableStateFlow(InventoryFilter.ALL)
    val inventoryFilter: StateFlow<InventoryFilter> = _inventoryFilter.asStateFlow()

    private val _cartQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _latestReceipt = MutableStateFlow<Sale?>(null)
    val latestReceipt: StateFlow<Sale?> = _latestReceipt.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val storeProfileState: StateFlow<UiState<StoreProfile?>> =
        repository.observeStoreProfile(currentUserId)
            .map<StoreProfile?, UiState<StoreProfile?>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreErrorOrRethrow(error, "Error observing profile")
                emit(UiState.Error(root.message ?: "No se pudo cargar la tienda"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val productsState: StateFlow<UiState<List<Product>>> =
        repository.observeProducts(currentUserId)
            .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreErrorOrRethrow(error, "Error observing products")
                emit(UiState.Error(root.message ?: "No se pudo cargar el catálogo"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val salesState: StateFlow<UiState<List<Sale>>> =
        repository.observeSales(currentUserId)
            .map<List<Sale>, UiState<List<Sale>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreErrorOrRethrow(error, "Error observing sales")
                emit(UiState.Error(root.message ?: "No se pudo cargar el historial de ventas"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val cartItems: StateFlow<List<CartItem>> = combine(
        productsState,
        _cartQuantities
    ) { prodState, quantities ->
        val products = (prodState as? UiState.Success)?.data ?: emptyList()
        val byId = products.associateBy { it.id }
        quantities.mapNotNull { (productId, qty) ->
            val product = byId[productId] ?: return@mapNotNull null
            if (!product.active || product.stock <= 0L) return@mapNotNull null
            val safeQty = qty.coerceIn(1, product.stock.toInt())
            CartItem(product = product, quantity = safeQty)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.ensureInitialCatalog(currentUserId, ownerEmail)
                .onFailure { error ->
                    _messages.tryEmit(error.message ?: "No se pudo inicializar el catálogo.")
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: ProductCategory?) {
        _selectedCategory.value = category
    }

    fun selectInventoryFilter(filter: InventoryFilter) {
        _inventoryFilter.value = filter
    }

    fun addToCart(product: Product) {
        if (!product.active || product.stock <= 0L) {
            _messages.tryEmit("Este producto no tiene stock disponible.")
            return
        }
        _cartQuantities.update { current ->
            val existing = current[product.id] ?: 0
            if (existing >= product.stock.toInt()) {
                _messages.tryEmit("Alcanzaste el máximo disponible (${product.stock}) para ${product.name}.")
                current
            } else {
                current + (product.id to (existing + 1))
            }
        }
    }

    fun updateCartQuantity(product: Product, newQuantity: Int) {
        _cartQuantities.update { current ->
            if (newQuantity <= 0) {
                current - product.id
            } else {
                val maxQty = product.stock.toInt().coerceAtLeast(1)
                current + (product.id to newQuantity.coerceAtMost(maxQty))
            }
        }
    }

    fun clearCart() {
        _cartQuantities.value = emptyMap()
    }

    fun dismissReceipt() {
        _latestReceipt.value = null
    }

    fun saveProduct(
        editingProductId: String?,
        name: String,
        description: String,
        category: ProductCategory,
        priceCents: Long,
        stock: Long,
        sku: String,
        imageKey: String,
        active: Boolean,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = if (editingProductId.isNullOrBlank()) {
                repository.addProduct(
                    userId = currentUserId,
                    name = name,
                    description = description,
                    category = category,
                    priceCents = priceCents,
                    stock = stock,
                    sku = sku,
                    imageKey = imageKey,
                    active = active
                )
            } else {
                repository.updateProduct(
                    userId = currentUserId,
                    productId = editingProductId,
                    name = name,
                    description = description,
                    category = category,
                    priceCents = priceCents,
                    stock = stock,
                    sku = sku,
                    imageKey = imageKey,
                    active = active
                )
            }
            _isMutating.value = false
            result.onSuccess {
                _messages.tryEmit(if (editingProductId == null) "Producto agregado al catálogo" else "Producto actualizado")
                onSaved()
            }.onFailure { error ->
                _messages.tryEmit(error.message ?: "No se pudo guardar el producto")
            }
        }
    }

    fun adjustProductStock(product: Product, delta: Long) {
        val target = (product.stock + delta).coerceIn(0L, 100_000L)
        if (target == product.stock) return
        viewModelScope.launch {
            repository.adjustStock(currentUserId, product.id, target)
                .onFailure { error ->
                    _messages.tryEmit(error.message ?: "No se pudo actualizar el stock")
                }
        }
    }

    fun toggleProductActive(product: Product, active: Boolean) {
        viewModelScope.launch {
            repository.toggleProductActive(currentUserId, product.id, active)
                .onFailure { error ->
                    _messages.tryEmit(error.message ?: "No se pudo cambiar la visibilidad")
                }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            _isMutating.value = true
            val res = repository.deleteProduct(currentUserId, product.id)
            _isMutating.value = false
            res.onSuccess {
                _cartQuantities.update { it - product.id }
                _messages.tryEmit("Producto eliminado")
            }.onFailure { error ->
                _messages.tryEmit(error.message ?: "No se pudo eliminar el producto")
            }
        }
    }

    fun checkoutCart(
        customerName: String,
        paymentMethod: PaymentMethod,
        notes: String,
        onCheckoutSuccess: () -> Unit,
    ) {
        val items = cartItems.value
        if (items.isEmpty()) {
            _messages.tryEmit("Tu carrito está vacío.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.completeSale(
                userId = currentUserId,
                cartItems = items,
                customerName = customerName,
                paymentMethod = paymentMethod,
                notes = notes
            )
            _isMutating.value = false
            result.onSuccess { sale ->
                _cartQuantities.value = emptyMap()
                _latestReceipt.value = sale
                onCheckoutSuccess()
            }.onFailure { error ->
                _messages.tryEmit(error.message ?: "Error al procesar la venta")
            }
        }
    }

    fun cancelSale(sale: Sale, onDone: () -> Unit) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.cancelSale(currentUserId, sale)
            _isMutating.value = false
            result.onSuccess {
                _messages.tryEmit("Venta cancelada y stock restaurado")
                onDone()
            }.onFailure { error ->
                _messages.tryEmit(error.message ?: "No se pudo cancelar la venta")
            }
        }
    }

    private fun unwrapFirestoreErrorOrRethrow(error: Throwable, logMessage: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, logMessage, rootError)
        return rootError
    }

    private companion object {
        const val TAG = "StoreVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
