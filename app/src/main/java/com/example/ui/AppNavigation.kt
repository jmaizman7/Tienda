package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.UiState
import com.example.data.repository.StoreRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.CartCheckoutSheet
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.SaleReceiptDialog
import com.example.ui.screens.SalesScreen
import com.example.ui.screens.StorefrontScreen
import com.example.ui.viewmodel.StoreViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class StoreTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
) {
    STORE(R.string.nav_store, Icons.Rounded.Storefront, Icons.Outlined.Storefront, "tab_store"),
    INVENTORY(R.string.nav_inventory, Icons.Rounded.Inventory2, Icons.Outlined.Inventory2, "tab_inventory"),
    SALES(R.string.nav_sales, Icons.Rounded.BarChart, Icons.Outlined.BarChart, "tab_sales"),
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            StoreWorkspace(
                currentUserId = user.uid,
                userEmail = user.email,
                userDisplayName = user.displayName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreWorkspace(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: StoreViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StoreViewModel(
                    repository = StoreRepository(db),
                    currentUserId = currentUserId,
                    ownerEmail = userEmail
                )
            }
        }
    )

    val profileState by viewModel.storeProfileState.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val salesState by viewModel.salesState.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val inventoryFilter by viewModel.inventoryFilter.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val latestReceipt by viewModel.latestReceipt.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(StoreTab.STORE) }
    var showCartSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (currentTab != StoreTab.STORE && !showCartSheet) {
        BackHandler { currentTab = StoreTab.STORE }
    }

    val storeTitle = (profileState as? UiState.Success)?.data?.storeName
        ?: stringResource(R.string.app_name)
    val cartCount = cartItems.sumOf { it.quantity }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = storeTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = userDisplayName?.takeIf { it.isNotBlank() }
                                ?: userEmail.orEmpty()
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                signOut(credentialManager = credentialManager, scope = scope)
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = stringResource(R.string.action_sign_out)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        StoreTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    TabIcon(
                                        tab = tab,
                                        selected = selected,
                                        cartCount = if (tab == StoreTab.STORE) cartCount else 0
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            StoreTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        TabIcon(
                                            tab = tab,
                                            selected = selected,
                                            cartCount = if (tab == StoreTab.STORE) cartCount else 0
                                        )
                                    },
                                    label = { Text(stringResource(tab.titleRes)) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        StoreTab.STORE -> StorefrontScreen(
                            productsState = productsState,
                            salesState = salesState,
                            cartItems = cartItems,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            onSearchChange = viewModel::updateSearchQuery,
                            onCategorySelected = viewModel::selectCategory,
                            onAddToCart = viewModel::addToCart,
                            onUpdateCartQty = viewModel::updateCartQuantity,
                            onOpenCart = { showCartSheet = true }
                        )
                        StoreTab.INVENTORY -> InventoryScreen(
                            productsState = productsState,
                            selectedFilter = inventoryFilter,
                            isMutating = isMutating,
                            onSelectFilter = viewModel::selectInventoryFilter,
                            onAdjustStock = viewModel::adjustProductStock,
                            onToggleActive = viewModel::toggleProductActive,
                            onDeleteProduct = viewModel::deleteProduct,
                            onSaveProduct = viewModel::saveProduct
                        )
                        StoreTab.SALES -> SalesScreen(
                            salesState = salesState,
                            isMutating = isMutating,
                            onCancelSale = viewModel::cancelSale
                        )
                    }
                }
            }
        }
    }

    if (showCartSheet) {
        CartCheckoutSheet(
            cartItems = cartItems,
            isMutating = isMutating,
            onUpdateQuantity = viewModel::updateCartQuantity,
            onClearCart = {
                viewModel.clearCart()
                showCartSheet = false
            },
            onConfirmSale = { customer, method, notes ->
                viewModel.checkoutCart(
                    customerName = customer,
                    paymentMethod = method,
                    notes = notes
                ) {
                    showCartSheet = false
                }
            },
            onDismiss = { showCartSheet = false }
        )
    }

    val receipt = latestReceipt
    if (receipt != null) {
        SaleReceiptDialog(
            sale = receipt,
            onDismiss = viewModel::dismissReceipt
        )
    }
}

@Composable
private fun TabIcon(
    tab: StoreTab,
    selected: Boolean,
    cartCount: Int,
) {
    val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
    if (cartCount > 0) {
        BadgedBox(
            badge = {
                Badge { Text(cartCount.toString()) }
            }
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    } else {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}
