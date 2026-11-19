package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PaymentMethod
import com.example.data.model.Sale
import com.example.data.model.SaleStatus
import com.example.data.model.UiState
import com.example.data.model.formatPriceCents
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.paymentMethodIcon
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonAlertContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SalesScreen(
    salesState: UiState<List<Sale>>,
    isMutating: Boolean,
    onCancelSale: (Sale, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedSale by remember { mutableStateOf<Sale?>(null) }
    var confirmCancelSale by remember { mutableStateOf<Sale?>(null) }

    when (salesState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "Error al cargar ventas",
                    subtitle = salesState.message
                )
            }
        }
        is UiState.Success -> {
            val allSales = salesState.data
            val validSales = remember(allSales) {
                allSales.filter { it.statusEnum == SaleStatus.COMPLETADA }
            }
            val totalRevenueCents = remember(validSales) {
                validSales.sumOf { it.totalCents }
            }
            val totalUnits = remember(validSales) {
                validSales.sumOf { it.totalItems }
            }
            val avgTicketCents = remember(validSales, totalRevenueCents) {
                if (validSales.isEmpty()) 0L else totalRevenueCents / validSales.size
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.fillMaxSize()
            ) {
                // KPI grid (2x2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SalesKpiCard(
                                label = stringResource(R.string.sales_revenue_total),
                                value = formatPriceCents(totalRevenueCents),
                                highlightPrimary = true,
                                modifier = Modifier.weight(1f)
                            )
                            SalesKpiCard(
                                label = stringResource(R.string.sales_completed_count),
                                value = validSales.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SalesKpiCard(
                                label = stringResource(R.string.sales_avg_ticket),
                                value = formatPriceCents(avgTicketCents),
                                modifier = Modifier.weight(1f)
                            )
                            SalesKpiCard(
                                label = stringResource(R.string.sales_units_sold),
                                value = totalUnits.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Payment method breakdown
                if (validSales.isNotEmpty()) {
                    item {
                        PaymentBreakdownCard(validSales = validSales, totalCents = totalRevenueCents)
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.sales_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (allSales.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.ReceiptLong,
                            title = stringResource(R.string.sales_empty_title),
                            subtitle = stringResource(R.string.sales_empty_subtitle)
                        )
                    }
                } else {
                    items(allSales, key = { it.id }) { sale ->
                        SaleHistoryRow(
                            sale = sale,
                            onClick = { selectedSale = sale }
                        )
                    }
                }
            }
        }
    }

    val currentSale = selectedSale
    if (currentSale != null) {
        SaleDetailDialog(
            sale = currentSale,
            onDismiss = { selectedSale = null },
            onRequestCancel = { confirmCancelSale = currentSale }
        )
    }

    val pendingCancel = confirmCancelSale
    if (pendingCancel != null) {
        AlertDialog(
            onDismissRequest = { confirmCancelSale = null },
            title = { Text(stringResource(R.string.sale_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.sale_cancel_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelSale(pendingCancel) {
                            confirmCancelSale = null
                            selectedSale = null
                        }
                    },
                    enabled = !isMutating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Sí, cancelar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancelSale = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SalesKpiCard(
    label: String,
    value: String,
    highlightPrimary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (highlightPrimary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlightPrimary) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (highlightPrimary) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PaymentBreakdownCard(
    validSales: List<Sale>,
    totalCents: Long,
) {
    val cashCents = validSales.filter { it.paymentMethodEnum == PaymentMethod.EFECTIVO }.sumOf { it.totalCents }
    val cardCents = validSales.filter { it.paymentMethodEnum == PaymentMethod.TARJETA }.sumOf { it.totalCents }
    val transferCents = validSales.filter { it.paymentMethodEnum == PaymentMethod.TRANSFERENCIA }.sumOf { it.totalCents }

    val safeTotal = totalCents.coerceAtLeast(1L).toFloat()
    val cashColor = MaterialTheme.colorScheme.primary
    val cardColor = MaterialTheme.colorScheme.secondary
    val transferColor = MaterialTheme.colorScheme.tertiary

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.sales_payment_breakdown),
                style = MaterialTheme.typography.titleSmall
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                if (cashCents > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((cashCents / safeTotal).coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(cashColor)
                    )
                }
                if (cardCents > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((cardCents / safeTotal).coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(cardColor)
                    )
                }
                if (transferCents > 0L) {
                    Box(
                        modifier = Modifier
                            .weight((transferCents / safeTotal).coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(transferColor)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PaymentLegendItem(
                    color = cashColor,
                    label = PaymentMethod.EFECTIVO.displayName,
                    amount = formatPriceCents(cashCents)
                )
                PaymentLegendItem(
                    color = cardColor,
                    label = PaymentMethod.TARJETA.displayName,
                    amount = formatPriceCents(cardCents)
                )
                PaymentLegendItem(
                    color = transferColor,
                    label = PaymentMethod.TRANSFERENCIA.displayName,
                    amount = formatPriceCents(transferCents)
                )
            }
        }
    }
}

@Composable
private fun PaymentLegendItem(color: Color, label: String, amount: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = amount,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SaleHistoryRow(
    sale: Sale,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM • HH:mm", Locale("es", "MX")) }
    val displayTime = remember(sale.createdAt) {
        val ts = sale.createdAt ?: Timestamp.now()
        dateFormat.format(ts.toDate())
    }
    val isCancelled = sale.statusEnum == SaleStatus.CANCELADA

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sale_row_${sale.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = paymentMethodIcon(sale.paymentMethodEnum),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sale.customerName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$displayTime • ${sale.totalItems} art. • ${sale.paymentMethodEnum.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatPriceCents(sale.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCancelled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Surface(
                    color = if (isCancelled) CrimsonAlertContainer else EmeraldSuccessContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = sale.statusEnum.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCancelled) CrimsonAlert else EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleDetailDialog(
    sale: Sale,
    onDismiss: () -> Unit,
    onRequestCancel: () -> Unit,
) {
    val isCompleted = sale.statusEnum == SaleStatus.COMPLETADA
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Detalle de venta",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cliente: ${sale.customerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Pago: ${sale.paymentMethodEnum.displayName} • Estado: ${sale.statusEnum.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sale.notes.isNotBlank()) {
                    Text(
                        text = "Nota: ${sale.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                sale.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.productName}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatPriceCents(item.subtotalCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Total", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = formatPriceCents(sale.totalCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isCompleted) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onRequestCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.sale_cancel_action))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
