package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PaymentMethod
import com.example.data.model.Product
import com.example.data.model.ProductCategory
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CrimsonAlertContainer
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer

fun bundledDrawableForKey(imageKey: String): Int? = when (imageKey.trim().lowercase()) {
    "headphones" -> R.drawable.img_product_headphones
    "coffee" -> R.drawable.img_product_coffee
    "backpack" -> R.drawable.img_product_backpack
    else -> null
}

fun categoryIcon(category: ProductCategory): ImageVector = when (category) {
    ProductCategory.ELECTRONICA -> Icons.Rounded.Headphones
    ProductCategory.HOGAR -> Icons.Rounded.Chair
    ProductCategory.ACCESORIOS -> Icons.Rounded.Watch
    ProductCategory.MODA -> Icons.Rounded.Checkroom
    ProductCategory.BEBIDAS -> Icons.Rounded.LocalCafe
    ProductCategory.OTROS -> Icons.Rounded.Category
}

fun paymentMethodIcon(method: PaymentMethod): ImageVector = when (method) {
    PaymentMethod.EFECTIVO -> Icons.Rounded.Payments
    PaymentMethod.TARJETA -> Icons.Rounded.CreditCard
    PaymentMethod.TRANSFERENCIA -> Icons.Rounded.AccountBalance
}

@Composable
fun ProductThumbnail(
    product: Product,
    modifier: Modifier = Modifier,
) {
    val resId = bundledDrawableForKey(product.imageKey)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = product.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        val cat = product.categoryEnum
        val gradientColors = when (cat) {
            ProductCategory.ELECTRONICA -> listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer
            )
            ProductCategory.HOGAR -> listOf(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.surfaceContainerHigh
            )
            ProductCategory.ACCESORIOS -> listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.primaryContainer
            )
            ProductCategory.MODA -> listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            )
            ProductCategory.BEBIDAS -> listOf(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.primaryContainer
            )
            ProductCategory.OTROS -> listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.secondaryContainer
            )
        }
        Box(
            modifier = modifier.background(Brush.linearGradient(gradientColors)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = categoryIcon(cat),
                        contentDescription = product.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StockStatusPill(
    stock: Long,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val (bg, fg, label) = when {
        !active -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Pausado"
        )
        stock <= 0L -> Triple(
            CrimsonAlertContainer,
            CrimsonAlert,
            stringResource(R.string.label_out_of_stock)
        )
        stock <= 5L -> Triple(
            AmberWarningContainer,
            AmberWarning,
            stringResource(R.string.label_low_stock, stock)
        )
        else -> Triple(
            EmeraldSuccessContainer,
            EmeraldSuccess,
            stringResource(R.string.label_in_stock, stock)
        )
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector = Icons.Rounded.ShoppingBag,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
