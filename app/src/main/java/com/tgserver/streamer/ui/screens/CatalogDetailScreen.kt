package com.tgserver.streamer.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tgserver.streamer.R
import com.tgserver.streamer.data.model.CatalogDefinition
import com.tgserver.streamer.data.model.MediaGroup
import com.tgserver.streamer.theme.BackgroundOled
import com.tgserver.streamer.ui.MainAppViewModel

private val NdotFont = FontFamily(Font(R.font.ndot))

@Composable
fun CatalogDetailScreen(
    catalog: CatalogDefinition,
    viewModel: MainAppViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val mediaGroups by viewModel.mediaGroups.collectAsState()

    var personFilmographyIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var companyFilmographyIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(catalog.filter.personTmdbId) {
        val pid = catalog.filter.personTmdbId
        if (pid != null && pid > 0) {
            personFilmographyIds = viewModel.getPersonFilmographyIds(pid)
        }
    }

    LaunchedEffect(catalog.filter.studioTmdbId) {
        val cid = catalog.filter.studioTmdbId
        if (cid != null && cid > 0) {
            companyFilmographyIds = viewModel.getCompanyFilmographyIds(cid)
        }
    }

    val matchedItems = remember(mediaGroups, catalog, personFilmographyIds, companyFilmographyIds) {
        viewModel.filterMediaForCatalog(catalog, personFilmographyIds, companyFilmographyIds)
    }

    var searchQuery by remember { mutableStateOf("") }
    val displayItems = remember(matchedItems, searchQuery) {
        if (searchQuery.isBlank()) matchedItems
        else matchedItems.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.year?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundOled)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBack()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1F24))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = catalog.name,
                                color = Color.White,
                                fontFamily = NdotFont,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (catalog.isAiPowered) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Powered",
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = "${matchedItems.size} items in category",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEdit()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1F24))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Filter Summary Pill
            val f = catalog.filter
            val criteriaLabels = mutableListOf<String>()
            when (f.mediaType) {
                "movie" -> criteriaLabels.add("Movies")
                "series" -> criteriaLabels.add("Series")
                else -> criteriaLabels.add("All Types")
            }
            if (f.personQuery.isNotBlank()) criteriaLabels.add(f.personQuery)
            if (f.studioQuery.isNotBlank()) criteriaLabels.add(f.studioQuery)
            if (f.genres.isNotEmpty()) criteriaLabels.addAll(f.genres.take(2))
            if (f.ratingFilterEnabled) criteriaLabels.add("${String.format("%.1f", f.minRating)}+")
            if (f.yearFilterEnabled) criteriaLabels.add("${f.minYear}-${f.maxYear}")

            Text(
                text = criteriaLabels.joinToString(" • "),
                color = Color(0xFFA855F7),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Search Bar inside Catalog
            if (matchedItems.size > 8) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search in this catalog...", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                    }
                )
            }

            // Grid of matched Media Items
            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            tint = Color(0xFF374151),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No matching items found in your library",
                            color = Color(0xFF9CA3AF),
                            fontFamily = NdotFont,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = "Add new movies or series to indexed channels",
                            color = Color(0xFF6B7280),
                            fontSize = 11.5.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(displayItems, key = { it.key }) { group ->
                        CatalogMediaCard(
                            group = group,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openMediaDetails(group.key)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogMediaCard(
    group: MediaGroup,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, Color(0xFF282A31)), RoundedCornerShape(12.dp))
        ) {
            RemoteImage(
                url = group.posterUrl,
                contentDescription = group.title,
                isSeries = group.type == "series" || group.type == "anime",
                modifier = Modifier.fillMaxSize()
            )

        }

        Text(
            text = group.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = group.year ?: (if (group.type == "series") "Series" else "Movie"),
            color = Color(0xFF8B909A),
            fontSize = 10.sp
        )
    }
}
