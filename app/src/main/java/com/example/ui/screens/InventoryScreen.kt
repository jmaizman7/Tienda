package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.data.model.UiState
import com.example.data.model.formatPriceCents
import com.example.data.model.parsePriceToCents
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.StockStatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.viewmodel.InventoryFilter
import java.util.Locale

@Composable
fun InventoryScreen(
    productsState: UiState<List<Product>>,
    selectedFilter: InventoryFilter,
    isMutating: Boolean,
    onSelectFilter: (InventoryFilter) -> Unit,
    onAdjustStock: (Product, Long) -> Unit,
    onToggleActive: (Product, Boolean) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onSaveProduct: (
        editingId: String?,
        name: String,
        description: String,
        category: ProductCategory,
        priceCents: Long,
        stock: Long,
        sku: String,
        imageKey: String,
        active: Boolean,
        onSaved: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (productsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Rounded.Inventory2,
                        title = "Error al cargar inventario",
                        subtitle = productsState.message
                    )
                }
            }
            is UiState.Success -> {
                val allProducts = productsState.data
                val lowStockCount = remember(allProducts) {
                    allProducts.count { it.stock in 1L..5L }
                }
                val totalValueCents = remember(allProducts) {
                    allProducts.sumOf { it.priceCents * it.stock }
                }

                val filtered = remember(allProducts, selectedFilter) {
                    when (selectedFilter) {
                        InventoryFilter.ALL -> allProducts
                        InventoryFilter.ACTIVE -> allProducts.filter { it.active }
                        InventoryFilter.LOW_STOCK -> allProducts.filter { it.stock in 1L..5L }
                        InventoryFilter.OUT_OF_STOCK -> allProducts.filter { it.stock <= 0L }
                        InventoryFilter.PAUSED -> allProducts.filter { !it.active }
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 92.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // KPI strip
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InventoryKpiCard(
                                label = stringResource(R.string.inventory_total_products),
                                value = allProducts.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            InventoryKpiCard(
                                label = stringResource(R.string.inventory_low_stock),
                                value = lowStockCount.toString(),
                                highlightWarning = lowStockCount > 0,
                                modifier = Modifier.weight(1f)
                            )
                            InventoryKpiCard(
                                label = stringResource(R.string.inventory_total_value),
                                value = formatPriceCents(totalValueCents),
                                modifier = Modifier.weight(1.35f)
                            )
                        }
                    }

                    // Filter chips
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(InventoryFilter.entries) { filter ->
                                val selected = selectedFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectFilter(filter) },
                                    label = { Text(filter.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Rounded.Inventory2,
                                title = "Sin productos en esta vista",
                                subtitle = "Cambia el filtro o agrega un nuevo producto con el botón inferior."
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { product ->
                            InventoryProductRow(
                                product = product,
                                onAdjustStock = { delta -> onAdjustStock(product, delta) },
                                onToggleActive = { active -> onToggleActive(product, active) },
                                onEdit = {
                                    editingProduct = product
                                    showEditor = true
                                },
                                onDelete = { productToDelete = product }
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editingProduct = null
                showEditor = true
            },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.inventory_new_product)) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_product_fab")
        )
    }

    if (showEditor) {
        ProductEditorSheet(
            initialProduct = editingProduct,
            isMutating = isMutating,
            onSave = { name, desc, cat, priceCents, stock, sku, imageKey, active ->
                onSaveProduct(
                    editingProduct?.id,
                    name,
                    desc,
                    cat,
                    priceCents,
                    stock,
                    sku,
                    imageKey,
                    active
                ) {
                    showEditor = false
                    editingProduct = null
                }
            },
            onDismiss = {
                showEditor = false
                editingProduct = null
            }
        )
    }

    val pendingDelete = productToDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("¿Eliminar producto?") },
            text = { Text("Se eliminará \"${pendingDelete.name}\" de tu catálogo permanentemente.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(pendingDelete)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun InventoryKpiCard(
    label: String,
    value: String,
    highlightWarning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlightWarning) AmberWarning else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InventoryProductRow(
    product: Product,
    onAdjustStock: (Long) -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_row_${product.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductThumbnail(
                    product = product,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = product.categoryEnum.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (product.sku.isNotBlank()) {
                            Text(
                                text = "• ${product.sku}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatPriceCents(product.priceCents),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        StockStatusPill(stock = product.stock, active = product.active)
                    }
                }
                Switch(
                    checked = product.active,
                    onCheckedChange = onToggleActive
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onAdjustStock(-1L) },
                            enabled = product.stock > 0L,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Restar stock")
                        }
                        Text(
                            text = "Stock: ${product.stock}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = { onAdjustStock(1L) },
                            enabled = product.stock < 100_000L,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Sumar stock")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Editar producto",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Eliminar producto",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditorSheet(
    initialProduct: Product?,
    isMutating: Boolean,
    onSave: (
        name: String,
        description: String,
        category: ProductCategory,
        priceCents: Long,
        stock: Long,
        sku: String,
        imageKey: String,
        active: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var description by remember { mutableStateOf(initialProduct?.description ?: "") }
    var priceText by remember {
        mutableStateOf(
            if (initialProduct != null && initialProduct.priceCents > 0L) {
                String.format(Locale.US, "%.2f", initialProduct.priceCents / 100.0)
            } else {
                ""
            }
        )
    }
    var stockText by remember {
        mutableStateOf(initialProduct?.stock?.toString() ?: "10")
    }
    var sku by remember { mutableStateOf(initialProduct?.sku ?: "") }
    var category by remember {
        mutableStateOf(initialProduct?.categoryEnum ?: ProductCategory.OTROS)
    }
    var imageKey by remember { mutableStateOf(initialProduct?.imageKey ?: "") }
    var active by remember { mutableStateOf(initialProduct?.active ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialProduct == null) {
                    stringResource(R.string.inventory_new_product)
                } else {
                    stringResource(R.string.inventory_edit_product)
                },
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text(stringResource(R.string.field_product_name)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_name_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.field_product_price)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("product_price_input")
                )
                OutlinedTextField(
                    value = stockText,
                    onValueChange = { stockText = it.filter { ch -> ch.isDigit() } },
                    label = { Text(stringResource(R.string.field_product_stock)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("product_stock_input")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.field_product_category),
                    style = MaterialTheme.typography.labelLarge
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProductCategory.entries) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.field_product_image),
                    style = MaterialTheme.typography.labelLarge
                )
                val presets = listOf(
                    "" to "Ícono de categoría",
                    "headphones" to "Audífonos",
                    "coffee" to "Cafetera",
                    "backpack" to "Mochila"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (key, label) ->
                        FilterChip(
                            selected = imageKey == key,
                            onClick = { imageKey = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = sku,
                onValueChange = { if (it.length <= 32) sku = it },
                label = { Text(stringResource(R.string.field_product_sku)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 300) description = it },
                label = { Text(stringResource(R.string.field_product_desc)) },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.field_product_active),
                    style = MaterialTheme.typography.titleSmall
                )
                Switch(checked = active, onCheckedChange = { active = it })
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        val cleanName = name.trim()
                        val parsedCents = parsePriceToCents(priceText)
                        val parsedStock = stockText.toLongOrNull()
                        when {
                            cleanName.isEmpty() -> {
                                validationError = "Ingresa un nombre válido (1 a 80 caracteres)."
                            }
                            parsedCents == null -> {
                                validationError = "Ingresa un precio válido mayor a $0.00."
                            }
                            parsedStock == null || parsedStock !in 0L..100_000L -> {
                                validationError = "El stock debe estar entre 0 y 100,000."
                            }
                            else -> {
                                validationError = null
                                onSave(
                                    cleanName,
                                    description.trim(),
                                    category,
                                    parsedCents,
                                    parsedStock,
                                    sku.trim(),
                                    imageKey,
                                    active
                                )
                            }
                        }
                    },
                    enabled = !isMutating,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("save_product_button")
                ) {
                    if (isMutating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
