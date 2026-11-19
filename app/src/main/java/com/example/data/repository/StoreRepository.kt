package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.CartItem
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.SaleStatus
import com.example.data.model.StoreProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthorizedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para continuar.")
        check(uid == expectedUserId) { "La sesión cambió; operación cancelada." }
        return uid
    }

    fun observeStoreProfile(userId: String): Flow<StoreProfile?> = flow {
        val uid = requireAuthorizedUid(userId)
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            StoreProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeProducts(userId: String): Flow<List<Product>> = flow {
        val uid = requireAuthorizedUid(userId)
        val colRef = db.collection("users").document(uid).collection("products")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Product::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeSales(userId: String): Flow<List<Sale>> = flow {
        val uid = requireAuthorizedUid(userId)
        val colRef = db.collection("users").document(uid).collection("sales")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Sale::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureInitialCatalog(userId: String, ownerEmail: String?): Result<Unit> {
        val userDocPath = "users/$userId"
        return try {
            val uid = requireAuthorizedUid(userId)
            val userDocRef = db.collection("users").document(uid)
            val snapshot = userDocRef.get().await()
            if (snapshot.exists()) {
                return Result.success(Unit)
            }

            val existingProducts = userDocRef.collection("products").limit(1).get().await()
            val batch = db.batch()
            val profilePayload = buildMap<String, Any> {
                put("userId", uid)
                put("storeName", "MiTienda")
                val safeEmail = ownerEmail?.trim()?.take(120).orEmpty()
                if (safeEmail.isNotEmpty()) {
                    put("ownerEmail", safeEmail)
                }
                put("catalogSeeded", true)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            batch.set(userDocRef, profilePayload)

            if (existingProducts.isEmpty) {
                val starterProducts = listOf(
                    StarterProduct(
                        name = "Audífonos Estudio Ámbar",
                        description = "Audio inalámbrico de alta fidelidad con almohadillas suaves y 30 h de batería.",
                        category = ProductCategory.ELECTRONICA,
                        priceCents = 149900L,
                        stock = 14L,
                        sku = "AUD-101",
                        imageKey = "headphones"
                    ),
                    StarterProduct(
                        name = "Cafetera Pour-Over Ébano",
                        description = "Jarra térmica con gotero de latón cepillado para extracción artesanal.",
                        category = ProductCategory.HOGAR,
                        priceCents = 89000L,
                        stock = 9L,
                        sku = "HOG-204",
                        imageKey = "coffee"
                    ),
                    StarterProduct(
                        name = "Mochila Urbana Terracota",
                        description = "Lona resistente al agua con detalles en piel y compartimento acolchado de 15\".",
                        category = ProductCategory.MODA,
                        priceCents = 124000L,
                        stock = 7L,
                        sku = "MOD-312",
                        imageKey = "backpack"
                    ),
                    StarterProduct(
                        name = "Café de Altura Veracruz 500g",
                        description = "Tostado medio artesanal en grano entero con notas de cacao y nuez.",
                        category = ProductCategory.BEBIDAS,
                        priceCents = 24500L,
                        stock = 24L,
                        sku = "BEB-018",
                        imageKey = ""
                    ),
                    StarterProduct(
                        name = "Funda Organizador Cuero",
                        description = "Estuche compacto para cables, cargadores y accesorios diarios.",
                        category = ProductCategory.ACCESORIOS,
                        priceCents = 38000L,
                        stock = 4L,
                        sku = "ACC-055",
                        imageKey = ""
                    ),
                    StarterProduct(
                        name = "Set 2 Tazas Cerámica Mate",
                        description = "Cerámica horneada a mano ideal para espresso o té matcha.",
                        category = ProductCategory.HOGAR,
                        priceCents = 32000L,
                        stock = 18L,
                        sku = "HOG-119",
                        imageKey = ""
                    )
                )

                starterProducts.forEach { item ->
                    val docRef = userDocRef.collection("products").document()
                    batch.set(
                        docRef,
                        mapOf(
                            "userId" to uid,
                            "name" to item.name,
                            "description" to item.description,
                            "category" to item.category.name,
                            "priceCents" to item.priceCents,
                            "stock" to item.stock,
                            "sku" to item.sku,
                            "imageKey" to item.imageKey,
                            "active" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDocPath)
            Result.failure(e)
        }
    }

    suspend fun addProduct(
        userId: String,
        name: String,
        description: String,
        category: ProductCategory,
        priceCents: Long,
        stock: Long,
        sku: String,
        imageKey: String,
        active: Boolean,
    ): Result<Unit> {
        val colPath = "users/$userId/products"
        return try {
            val uid = requireAuthorizedUid(userId)
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "El nombre del producto es obligatorio." }
            require(priceCents in 1L..100_000_000L) { "El precio debe ser mayor a $0.00." }
            require(stock in 0L..100_000L) { "El stock debe estar entre 0 y 100,000." }

            val docRef = db.collection("users").document(uid).collection("products").document()
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "description" to description.trim().take(300),
                "category" to category.name,
                "priceCents" to priceCents,
                "stock" to stock,
                "sku" to sku.trim().take(32),
                "imageKey" to imageKey.trim().take(64),
                "active" to active,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colPath)
            Result.failure(e)
        }
    }

    suspend fun updateProduct(
        userId: String,
        productId: String,
        name: String,
        description: String,
        category: ProductCategory,
        priceCents: Long,
        stock: Long,
        sku: String,
        imageKey: String,
        active: Boolean,
    ): Result<Unit> {
        val docPath = "users/$userId/products/$productId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(productId.isNotBlank()) { "ID de producto inválido." }
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "El nombre del producto es obligatorio." }
            require(priceCents in 1L..100_000_000L) { "El precio debe ser mayor a $0.00." }
            require(stock in 0L..100_000L) { "El stock debe estar entre 0 y 100,000." }

            val docRef = db.collection("users").document(uid).collection("products").document(productId)
            docRef.update(
                mapOf(
                    "name" to cleanName,
                    "description" to description.trim().take(300),
                    "category" to category.name,
                    "priceCents" to priceCents,
                    "stock" to stock,
                    "sku" to sku.trim().take(32),
                    "imageKey" to imageKey.trim().take(64),
                    "active" to active,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun adjustStock(userId: String, productId: String, newStock: Long): Result<Unit> {
        val docPath = "users/$userId/products/$productId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(productId.isNotBlank()) { "ID de producto inválido." }
            val clamped = newStock.coerceIn(0L, 100_000L)
            db.collection("users").document(uid).collection("products").document(productId)
                .update(
                    mapOf(
                        "stock" to clamped,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleProductActive(userId: String, productId: String, active: Boolean): Result<Unit> {
        val docPath = "users/$userId/products/$productId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(productId.isNotBlank()) { "ID de producto inválido." }
            db.collection("users").document(uid).collection("products").document(productId)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(userId: String, productId: String): Result<Unit> {
        val docPath = "users/$userId/products/$productId"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(productId.isNotBlank()) { "ID de producto inválido." }
            db.collection("users").document(uid).collection("products").document(productId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun completeSale(
        userId: String,
        cartItems: List<CartItem>,
        customerName: String,
        paymentMethod: PaymentMethod,
        notes: String,
    ): Result<Sale> {
        val salesColPath = "users/$userId/sales"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(cartItems.isNotEmpty() && cartItems.size <= 30) {
                "El carrito debe tener entre 1 y 30 productos distintos."
            }
            val cleanCustomer = customerName.trim().ifEmpty { "Cliente Mostrador" }.take(80)
            val cleanNotes = notes.trim().take(200)

            val userRef = db.collection("users").document(uid)
            val saleRef = userRef.collection("sales").document()

            val completedSale = db.runTransaction { transaction ->
                val productSnapshots = cartItems.map { cartItem ->
                    val prodRef = userRef.collection("products").document(cartItem.product.id)
                    val snap = transaction.get(prodRef)
                    Triple(cartItem, prodRef, snap)
                }

                val saleItemMaps = mutableListOf<Map<String, Any>>()
                val saleItems = mutableListOf<SaleItem>()
                var totalCents = 0L
                var totalUnits = 0L

                for ((cartItem, prodRef, snap) in productSnapshots) {
                    if (!snap.exists()) {
                        throw IllegalStateException("El producto '${cartItem.product.name}' ya no existe.")
                    }
                    val currentStock = snap.getLong("stock") ?: 0L
                    val qty = cartItem.quantity.toLong()
                    if (qty < 1L || currentStock < qty) {
                        throw IllegalStateException(
                            "Stock insuficiente para '${cartItem.product.name}' (disponibles: $currentStock)."
                        )
                    }
                    val unitPrice = snap.getLong("priceCents") ?: cartItem.product.priceCents
                    val prodName = (snap.getString("name") ?: cartItem.product.name).take(80)
                    val subtotal = unitPrice * qty

                    transaction.update(
                        prodRef,
                        mapOf(
                            "stock" to (currentStock - qty),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )

                    saleItemMaps.add(
                        mapOf(
                            "productId" to cartItem.product.id,
                            "productName" to prodName,
                            "quantity" to qty,
                            "unitPriceCents" to unitPrice,
                            "subtotalCents" to subtotal
                        )
                    )
                    saleItems.add(
                        SaleItem(
                            productId = cartItem.product.id,
                            productName = prodName,
                            quantity = qty,
                            unitPriceCents = unitPrice,
                            subtotalCents = subtotal
                        )
                    )
                    totalCents += subtotal
                    totalUnits += qty
                }

                val salePayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("customerName", cleanCustomer)
                    put("paymentMethod", paymentMethod.name)
                    put("status", SaleStatus.COMPLETADA.name)
                    put("items", saleItemMaps)
                    put("totalCents", totalCents)
                    put("totalItems", totalUnits)
                    if (cleanNotes.isNotEmpty()) {
                        put("notes", cleanNotes)
                    }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                transaction.set(saleRef, salePayload)

                Sale(
                    id = saleRef.id,
                    userId = uid,
                    customerName = cleanCustomer,
                    paymentMethod = paymentMethod.name,
                    status = SaleStatus.COMPLETADA.name,
                    items = saleItems,
                    totalCents = totalCents,
                    totalItems = totalUnits,
                    notes = cleanNotes,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            }.await()

            Result.success(completedSale)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, salesColPath)
            Result.failure(e)
        }
    }

    suspend fun cancelSale(userId: String, sale: Sale): Result<Unit> {
        val salePath = "users/$userId/sales/${sale.id}"
        return try {
            val uid = requireAuthorizedUid(userId)
            require(sale.id.isNotBlank()) { "ID de venta inválido." }
            val userRef = db.collection("users").document(uid)
            val saleRef = userRef.collection("sales").document(sale.id)

            db.runTransaction { transaction ->
                val saleSnap = transaction.get(saleRef)
                if (!saleSnap.exists()) {
                    throw IllegalStateException("La venta no existe.")
                }
                val currentStatus = saleSnap.getString("status")
                if (currentStatus == SaleStatus.CANCELADA.name) {
                    throw IllegalStateException("Esta venta ya fue cancelada.")
                }

                val productSnaps = sale.items.mapNotNull { item ->
                    if (item.productId.isBlank()) null
                    else {
                        val prodRef = userRef.collection("products").document(item.productId)
                        Triple(item, prodRef, transaction.get(prodRef))
                    }
                }

                for ((item, prodRef, prodSnap) in productSnaps) {
                    if (prodSnap.exists()) {
                        val existingStock = prodSnap.getLong("stock") ?: 0L
                        val restored = (existingStock + item.quantity).coerceAtMost(100_000L)
                        transaction.update(
                            prodRef,
                            mapOf(
                                "stock" to restored,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                transaction.update(
                    saleRef,
                    mapOf(
                        "status" to SaleStatus.CANCELADA.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, salePath)
            Result.failure(e)
        }
    }

    private data class StarterProduct(
        val name: String,
        val description: String,
        val category: ProductCategory,
        val priceCents: Long,
        val stock: Long,
        val sku: String,
        val imageKey: String,
    )
}
