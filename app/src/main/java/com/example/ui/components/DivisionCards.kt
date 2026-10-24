package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Level2Type
import com.example.data.model.Subdivision
import com.example.ui.theme.Radius
import com.example.ui.theme.Spacing
import com.example.ui.util.AttendeePricing
import com.example.ui.util.ListingDivisions
import com.example.ui.util.SpaceCalculationUtils
import com.example.ui.util.localizedLabel

/**
 * Step 3 of Create Listing: one card per configured division (photo or type icon, name, type, lowest price) and a dashed
 * "Add division" tile. Tapping a card edits it in [DivisionSheet]. One division is the whole place
 * ([ListingDivisions.isWholePlace]); the caption above the cards says which.
 */
@Composable
fun DivisionCards(
    divisions: List<Subdivision>,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = when {
                divisions.isEmpty() -> stringResource(R.string.divisions_empty_hint)
                ListingDivisions.isWholePlace(divisions) -> stringResource(R.string.divisions_whole_place)
                else -> pluralStringResource(R.plurals.divisions_count, divisions.size, divisions.size)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        divisions.forEachIndexed { index, division ->
            DivisionCard(division = division, onClick = { onEdit(index) }, onDelete = { onDelete(index) })
        }
        AddDivisionTile(onClick = onAdd)
    }
}

/** One division as a card; also the division sheet's live preview (no click, no delete). */
@Composable
internal fun DivisionCard(division: Subdivision, onClick: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val price = SpaceCalculationUtils.findLowestPriceForSubdivision(division)
    val priceText = if (price.amount > 0.0) "$" + AttendeePricing.formatUsd(price.amount) + price.unitLabel else null
    ProCard(
        variant = CardVariant.Outlined,
        onClick = onClick,
        contentPadding = PaddingValues(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val photo = division.imageUrls.firstOrNull()
                if (photo != null) {
                    AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                } else {
                    Icon(iconFor(division.type), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    division.name.ifBlank { localizedLabel(division.type.displayName) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(localizedLabel(division.type.displayName), priceText).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (priceText == null) {
                    Text(
                        stringResource(R.string.division_needs_price),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_division), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AddDivisionTile(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.large)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(Radius.lg.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                )
            }
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = outline)
        Text(
            stringResource(R.string.add_division),
            modifier = Modifier.padding(start = Spacing.sm),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = outline
        )
    }
}

internal fun iconFor(type: Level2Type): ImageVector = when (type) {
    Level2Type.OFFICE -> Icons.Default.Business
    Level2Type.CONFERENCE_ROOM, Level2Type.THEATER_TRAINING, Level2Type.TRAINING_ROOM -> Icons.Default.Groups
    Level2Type.DESK_IN_SHARED_AREA, Level2Type.STORAGE -> Icons.Default.Apartment
    Level2Type.GYM, Level2Type.SPORTS_AREA, Level2Type.STUDIO -> Icons.Default.Storefront
    Level2Type.ROOMS -> Icons.Default.MeetingRoom
}
