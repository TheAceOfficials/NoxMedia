package com.tgserver.streamer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tgserver.streamer.R
import com.tgserver.streamer.data.model.CatalogDefinition
import com.tgserver.streamer.theme.BackgroundOled
import com.tgserver.streamer.ui.MainAppViewModel
import java.util.Collections

private val NdotFont = FontFamily(Font(R.font.ndot))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CatalogsScreen(viewModel: MainAppViewModel) {
    val haptic = LocalHapticFeedback.current
    val catalogs by viewModel.catalogs.collectAsState()
    val mediaGroups by viewModel.mediaGroups.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedCatalogIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var catalogToEdit by remember { mutableStateOf<CatalogDefinition?>(null) }
    var showAddCatalogSheet by remember { mutableStateOf(false) }
    var selectedCatalogDetail by remember { mutableStateOf<CatalogDefinition?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Local copy of catalogs for fluid drag & drop reordering
    var localList by remember(catalogs) { mutableStateOf(catalogs) }

    // Filtered catalogs if search active
    val displayedCatalogs = remember(localList, searchQuery) {
        if (searchQuery.isBlank()) localList
        else localList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.filter.personQuery.contains(searchQuery, ignoreCase = true) ||
            it.filter.studioQuery.contains(searchQuery, ignoreCase = true) ||
            it.filter.genres.any { g -> g.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Drag and drop state
    var draggedIndex by remember { mutableStateOf<Int?>(null) }

    if (selectedCatalogDetail != null) {
        CatalogDetailScreen(
            catalog = selectedCatalogDetail!!,
            viewModel = viewModel,
            onBack = { selectedCatalogDetail = null },
            onEdit = {
                val cat = selectedCatalogDetail
                selectedCatalogDetail = null
                catalogToEdit = cat
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Branding / Multi-Select Mode Bar
            AnimatedContent(
                targetState = isMultiSelectMode,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "header_transition"
            ) { multiSelectActive ->
                if (multiSelectActive) {
                    // Multi-Select Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1F24))
                                    .clickable {
                                        isMultiSelectMode = false
                                        selectedCatalogIds = emptySet()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = "${selectedCatalogIds.size} Selected",
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 14.5.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedCatalogIds = if (selectedCatalogIds.size == displayedCatalogs.size) emptySet()
                                    else displayedCatalogs.map { it.id }.toSet()
                                }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedCatalogIds.size == displayedCatalogs.size) "Clear" else "Select All",
                                color = Color(0xFFA855F7),
                                fontFamily = NdotFont,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                } else {
                    // Normal Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "Catalogs",
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 20.sp,
                                letterSpacing = 0.8.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Smart Curations & Shelves",
                                color = Color(0xFF8B909A),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Multi-Select Trigger
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1F24))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectMode = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Select Multiple",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Add Catalog Button
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFA855F7))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAddCatalogSheet = true
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "New Catalog",
                                        color = Color.White,
                                        fontFamily = NdotFont,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar
            if (catalogs.size > 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search catalogs...", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF16171B),
                        unfocusedContainerColor = Color(0xFF16171B),
                        focusedBorderColor = Color(0xFFA855F7),
                        unfocusedBorderColor = Color(0xFF282A31),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
            }

            // List of Catalogs with Drag & Drop Reordering
            if (displayedCatalogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No catalogs match your search" else "No catalogs created yet",
                            color = Color(0xFF9CA3AF),
                            fontFamily = NdotFont,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Tap 'New Catalog' above to build a normal or AI-powered collection",
                            color = Color(0xFF6B7280),
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    itemsIndexed(displayedCatalogs, key = { _, item -> item.id }) { index, catalog ->
                        val isSelected = selectedCatalogIds.contains(catalog.id)
                        val isDraggingThis = draggedIndex == index

                        val matchCount = remember(mediaGroups, catalog) {
                            viewModel.filterMediaForCatalog(catalog).size
                        }

                        CatalogItemCard(
                            catalog = catalog,
                            matchCount = matchCount,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = isSelected,
                            isDragging = isDraggingThis,
                            onToggleSelect = {
                                selectedCatalogIds = if (isSelected) selectedCatalogIds - catalog.id else selectedCatalogIds + catalog.id
                            },
                            onCardClick = {
                                if (isMultiSelectMode) {
                                    selectedCatalogIds = if (isSelected) selectedCatalogIds - catalog.id else selectedCatalogIds + catalog.id
                                } else {
                                    selectedCatalogDetail = catalog
                                }
                            },
                            onCardLongClick = {
                                if (!isMultiSelectMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isMultiSelectMode = true
                                    selectedCatalogIds = setOf(catalog.id)
                                }
                            },
                            onPinToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleCatalogPin(catalog.id)
                            },
                            onHideToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleCatalogHide(catalog.id)
                            },
                            onEdit = {
                                catalogToEdit = catalog
                            },
                            onMoveUp = if (index > 0 && searchQuery.isBlank() && !isMultiSelectMode) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val mutable = localList.toMutableList()
                                    Collections.swap(mutable, index, index - 1)
                                    localList = mutable
                                    viewModel.reorderCatalogs(mutable.map { it.id })
                                }
                            } else null,
                            onMoveDown = if (index < displayedCatalogs.size - 1 && searchQuery.isBlank() && !isMultiSelectMode) {
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val mutable = localList.toMutableList()
                                    Collections.swap(mutable, index, index + 1)
                                    localList = mutable
                                    viewModel.reorderCatalogs(mutable.map { it.id })
                                }
                            } else null
                        )
                    }
                }
            }
        }

        // Floating Bottom Multi-Select Actions Bar
        AnimatedVisibility(
            visible = isMultiSelectMode && selectedCatalogIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xF0181A22))
                    .border(BorderStroke(1.dp, Color(0xFF383C4D)), RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bulk Toggle Hide
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedCatalogIds.forEach { id -> viewModel.toggleCatalogHide(id) }
                        isMultiSelectMode = false
                        selectedCatalogIds = emptySet()
                    }
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Toggle Hide", color = Color.White, fontFamily = NdotFont, fontSize = 11.5.sp)
                }

                Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0xFF383C4D)))

                // Bulk Delete
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteConfirmDialog = true
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete (${selectedCatalogIds.size})", color = Color(0xFFEF4444), fontFamily = NdotFont, fontSize = 11.5.sp)
                }
            }
        }
    }

    // Modal Sheet for Adding New Catalog
    if (showAddCatalogSheet) {
        CatalogEditorSheet(
            initialCatalog = null,
            viewModel = viewModel,
            onDismiss = { showAddCatalogSheet = false },
            onSaved = { newCat ->
                showAddCatalogSheet = false
                viewModel.addCatalog(newCat)
            }
        )
    }

    // Modal Sheet for Editing Existing Catalog
    if (catalogToEdit != null) {
        CatalogEditorSheet(
            initialCatalog = catalogToEdit,
            viewModel = viewModel,
            onDismiss = { catalogToEdit = null },
            onSaved = { updatedCat ->
                catalogToEdit = null
                viewModel.updateCatalog(updatedCat)
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete Catalogs",
                    fontFamily = NdotFont,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete ${selectedCatalogIds.size} catalog(s)? This will not delete any media files.",
                    color = Color(0xFFD1D5DB),
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCatalogs(selectedCatalogIds)
                        showDeleteConfirmDialog = false
                        isMultiSelectMode = false
                        selectedCatalogIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", color = Color.White, fontFamily = NdotFont, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF9CA3AF), fontSize = 12.5.sp)
                }
            },
            containerColor = Color(0xFF1E1F24),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogItemCard(
    catalog: CatalogDefinition,
    matchCount: Int,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isDragging: Boolean,
    onToggleSelect: () -> Unit,
    onCardClick: () -> Unit,
    onCardLongClick: () -> Unit,
    onPinToggle: () -> Unit,
    onHideToggle: () -> Unit,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    val haptic = LocalHapticFeedback.current

    val elevation by animateFloatAsState(
        targetValue = if (isDragging) 16f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "card_elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 1f)
            .shadow(elevation.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) Color(0xFFA855F7)
                    else if (catalog.isAiPowered) Color(0xFF6D28D9).copy(alpha = 0.5f)
                    else Color(0xFF282A31)
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCardClick,
                onLongClick = onCardLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (catalog.isHidden) Color(0xFF121316)
            else if (catalog.isAiPowered) Color(0xFF161420)
            else Color(0xFF18191E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Upper Row: Selection Checkbox / AI Indicator + Title + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isMultiSelectMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFFA855F7),
                                uncheckedColor = Color(0xFF6B7280)
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // AI or Folder Icon Badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (catalog.isAiPowered) Color(0xFFA855F7).copy(alpha = 0.2f)
                                else Color(0xFF242630)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (catalog.isAiPowered) Icons.Default.AutoAwesome else Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = if (catalog.isAiPowered) Color(0xFFA855F7) else Color(0xFFD1D5DB),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = catalog.name,
                                color = if (catalog.isHidden) Color(0xFF8B909A) else Color.White,
                                fontFamily = NdotFont,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (catalog.isHidden) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF282A31))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("Hidden", color = Color(0xFF9CA3AF), fontSize = 9.sp)
                                }
                            }
                        }

                        // Summary of filters
                        val f = catalog.filter
                        val pills = mutableListOf<String>()
                        when (f.mediaType) {
                            "movie" -> pills.add("Movies")
                            "series" -> pills.add("Series")
                            else -> pills.add("All")
                        }
                        if (f.personQuery.isNotBlank()) pills.add(f.personQuery)
                        if (f.studioQuery.isNotBlank()) pills.add(f.studioQuery)
                        if (f.genres.isNotEmpty()) pills.add(f.genres.first())
                        if (f.ratingFilterEnabled) pills.add("${String.format("%.1f", f.minRating)}+")

                        Text(
                            text = pills.joinToString(" • "),
                            color = Color(0xFF8B909A),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Controls: Pin, Hide, Edit, Reorder Arrows
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Up / Down nudge reorder buttons
                    if (onMoveUp != null) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onMoveDown != null) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Pin to Home
                    IconButton(onClick = onPinToggle, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin to Home",
                            tint = if (catalog.pinToHome) Color(0xFFA855F7) else Color(0xFF555966),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Edit
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF9CA3AF), modifier = Modifier.size(15.dp))
                    }
                }
            }

            // Lower Info Bar: Match count + Pin status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF22242C))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$matchCount titles available",
                        color = Color(0xFFD1D5DB),
                        fontFamily = NdotFont,
                        fontSize = 10.sp
                    )
                }

                if (catalog.pinToHome) {
                    Text(
                        text = "Pinned on Home",
                        color = Color(0xFFA855F7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
