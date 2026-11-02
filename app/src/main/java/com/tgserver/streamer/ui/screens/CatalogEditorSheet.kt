package com.tgserver.streamer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tgserver.streamer.R
import com.tgserver.streamer.data.model.*
import com.tgserver.streamer.theme.BackgroundOled
import com.tgserver.streamer.ui.AppTab
import com.tgserver.streamer.ui.MainAppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val NdotFont = FontFamily(Font(R.font.ndot))

private val STANDARD_GENRES = listOf(
    "Action", "Adventure", "Animation", "Comedy", "Crime",
    "Drama", "Fantasy", "Horror", "Mystery", "Romance",
    "Science Fiction", "Thriller", "War"
)

private val STANDARD_STUDIOS = listOf(
    "Studio Ghibli", "Netflix", "Prime Video", "Disney+",
    "HBO", "Apple TV+", "Warner Bros", "Marvel", "A24"
)

private val STANDARD_LANGUAGES = listOf(
    "English", "Hindi", "Japanese", "Korean", "Dual Audio", "Multi Audio"
)

private val AI_PROMPT_INSPIRATIONS = listOf(
    "Mind-bending time travel thrillers",
    "Dark aesthetic cyberpunk sci-fi",
    "Studio Ghibli style magical fantasy",
    "High rated 90s action classics",
    "Gripping crime noir detective mystery"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CatalogEditorSheet(
    initialCatalog: CatalogDefinition? = null,
    viewModel: MainAppViewModel,
    onDismiss: () -> Unit,
    onSaved: (CatalogDefinition) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val config by viewModel.config.collectAsState()
    val mediaGroups by viewModel.mediaGroups.collectAsState()

    val hasAiKey = (config.aiProvider == "gemini" && config.geminiApiKey.isNotBlank()) ||
            (config.aiProvider == "openrouter" && config.openRouterApiKey.isNotBlank())

    // Form states
    var catalogName by remember { mutableStateOf(initialCatalog?.name.orEmpty()) }
    var isAiPowered by remember { mutableStateOf(initialCatalog?.isAiPowered ?: false) }
    var aiPrompt by remember { mutableStateOf(initialCatalog?.aiPrompt.orEmpty()) }
    var pinToHome by remember { mutableStateOf(initialCatalog?.pinToHome ?: true) }

    val initialFilter = initialCatalog?.filter ?: CatalogFilterCriteria()
    var mediaType by remember { mutableStateOf(initialFilter.mediaType) } // "all", "movie", "series"
    var personQuery by remember { mutableStateOf(initialFilter.personQuery) }
    var selectedPersonId by remember { mutableStateOf(initialFilter.personTmdbId) }
    var studioQuery by remember { mutableStateOf(initialFilter.studioQuery) }
    var selectedGenres by remember { mutableStateOf(initialFilter.genres.toSet()) }
    var selectedLanguages by remember { mutableStateOf(initialFilter.languages.toSet()) }

    var ratingFilterEnabled by remember { mutableStateOf(initialFilter.ratingFilterEnabled) }
    var minRating by remember { mutableFloatStateOf(initialFilter.minRating) }
    var maxRating by remember { mutableFloatStateOf(initialFilter.maxRating) }

    var yearFilterEnabled by remember { mutableStateOf(initialFilter.yearFilterEnabled) }
    var minYear by remember { mutableIntStateOf(initialFilter.minYear) }
    var maxYear by remember { mutableIntStateOf(initialFilter.maxYear) }

    var sortBy by remember { mutableStateOf(initialFilter.sortBy) }
    var sortAscending by remember { mutableStateOf(initialFilter.sortAscending) }
    var semanticKeywords by remember { mutableStateOf(initialFilter.keywords) }

    // UI state for expandable sections
    var isAiExpanded by remember { mutableStateOf(hasAiKey && initialCatalog == null) }
    var isAdvancedExpanded by remember { mutableStateOf(initialCatalog != null || !hasAiKey) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var showApiKeyPromptDialog by remember { mutableStateOf(false) }

    // Person search results
    var personSearchResults by remember { mutableStateOf<List<TmdbPersonItem>>(emptyList()) }
    var isSearchingPeople by remember { mutableStateOf(false) }

    // Debounced TMDB Person Search
    LaunchedEffect(personQuery) {
        if (personQuery.length >= 3 && selectedPersonId == null && config.tmdbApiKey.isNotBlank()) {
            delay(350)
            isSearchingPeople = true
            personSearchResults = viewModel.searchTmdbPeople(personQuery)
            isSearchingPeople = false
        } else {
            personSearchResults = emptyList()
        }
    }

    // Person Filmography IDs
    var personFilmographyIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(selectedPersonId) {
        val id = selectedPersonId
        if (id != null && id > 0) {
            personFilmographyIds = viewModel.getPersonFilmographyIds(id)
        } else {
            personFilmographyIds = emptySet()
        }
    }

    // Live preview of matching items in library
    val liveMatchingCount = remember(
        mediaGroups, mediaType, personQuery, selectedPersonId, personFilmographyIds,
        studioQuery, selectedGenres, selectedLanguages,
        ratingFilterEnabled, minRating, maxRating,
        yearFilterEnabled, minYear, maxYear,
        sortBy, sortAscending, semanticKeywords
    ) {
        val tempFilter = CatalogFilterCriteria(
            mediaType = mediaType,
            personQuery = personQuery,
            personTmdbId = selectedPersonId,
            studioQuery = studioQuery,
            genres = selectedGenres.toList(),
            languages = selectedLanguages.toList(),
            ratingFilterEnabled = ratingFilterEnabled,
            minRating = minRating,
            maxRating = maxRating,
            yearFilterEnabled = yearFilterEnabled,
            minYear = minYear,
            maxYear = maxYear,
            sortBy = sortBy,
            sortAscending = sortAscending,
            keywords = semanticKeywords
        )
        val tempCatalog = CatalogDefinition(name = "Preview", filter = tempFilter)
        viewModel.filterMediaForCatalog(tempCatalog, personFilmographyIds).size
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundOled)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Top App Bar: Close + Title + Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1F24))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = if (initialCatalog != null) "Edit Catalog" else "New Catalog",
                        color = Color.White,
                        fontFamily = NdotFont,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp
                    )

                    Button(
                        onClick = {
                            val finalName = catalogName.trim().ifBlank {
                                if (personQuery.isNotBlank()) "Films by $personQuery"
                                else if (studioQuery.isNotBlank()) "$studioQuery Collection"
                                else if (selectedGenres.isNotEmpty()) "${selectedGenres.first()} Spotlight"
                                else "Custom Catalog"
                            }
                            val finalFilter = CatalogFilterCriteria(
                                mediaType = mediaType,
                                personQuery = personQuery.trim(),
                                personTmdbId = selectedPersonId,
                                studioQuery = studioQuery.trim(),
                                genres = selectedGenres.toList(),
                                languages = selectedLanguages.toList(),
                                ratingFilterEnabled = ratingFilterEnabled,
                                minRating = minRating,
                                maxRating = maxRating,
                                yearFilterEnabled = yearFilterEnabled,
                                minYear = minYear,
                                maxYear = maxYear,
                                sortBy = sortBy,
                                sortAscending = sortAscending,
                                keywords = semanticKeywords
                            )
                            val finalCatalog = (initialCatalog ?: CatalogDefinition(name = finalName)).copy(
                                name = finalName,
                                isAiPowered = isAiPowered,
                                aiPrompt = if (isAiPowered) aiPrompt.trim().takeIf { it.isNotBlank() } else null,
                                pinToHome = pinToHome,
                                filter = finalFilter
                            )
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSaved(finalCatalog)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Save",
                            color = Color.White,
                            fontFamily = NdotFont,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Section 1: Catalog Name Field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "CATALOG TITLE",
                        color = Color(0xFF9CA3AF),
                        fontFamily = NdotFont,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.6.sp
                    )

                    OutlinedTextField(
                        value = catalogName,
                        onValueChange = { catalogName = it },
                        placeholder = {
                            Text(
                                "e.g. Christopher Nolan Essentials, 90s Cyberpunk",
                                color = Color(0xFF6B7280),
                                fontSize = 13.5.sp
                            )
                        },
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
                        )
                    )
                }

                // Section 2: "Build with AI" Interactive Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            BorderStroke(
                                1.dp,
                                if (hasAiKey) Color(0xFF6D28D9).copy(alpha = 0.7f) else Color(0xFF282A31)
                            ),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            if (!hasAiKey) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showApiKeyPromptDialog = true
                            } else {
                                isAiExpanded = !isAiExpanded
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasAiKey) Color(0xFF171322) else Color(0xFF141519)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header row of the AI Card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (hasAiKey) Color(0xFFA855F7).copy(alpha = 0.2f) else Color(0xFF22242C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (hasAiKey) Icons.Default.AutoAwesome else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (hasAiKey) Color(0xFFA855F7) else Color(0xFF6B7280),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Build with AI",
                                        color = if (hasAiKey) Color.White else Color(0xFF9CA3AF),
                                        fontFamily = NdotFont,
                                        fontSize = 13.5.sp,
                                        letterSpacing = 0.4.sp
                                    )
                                    Text(
                                        text = if (hasAiKey) "Powered by ${if (config.aiProvider == "openrouter") "OpenRouter" else "Gemini"}"
                                        else "API key required to unlock",
                                        color = if (hasAiKey) Color(0xFFC084FC) else Color(0xFF6B7280),
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            if (hasAiKey) {
                                Icon(
                                    imageVector = if (isAiExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF252731))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Add Key",
                                        color = Color(0xFFE5E7EB),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Expanded Prompt Workspace
                        AnimatedVisibility(
                            visible = isAiExpanded && hasAiKey,
                            enter = expandVertically(animationSpec = spring(dampingRatio = 0.78f)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.78f)) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = aiPrompt,
                                    onValueChange = { aiPrompt = it },
                                    placeholder = {
                                        Text(
                                            "Describe what you want to watch (e.g. 'Mind-bending sci-fi thrillers from the 2000s with high rating')",
                                            color = Color(0xFF6B7280),
                                            fontSize = 12.5.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF110E1A),
                                        unfocusedContainerColor = Color(0xFF110E1A),
                                        focusedBorderColor = Color(0xFFA855F7),
                                        unfocusedBorderColor = Color(0xFF382554),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                // Quick Inspiration Chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AI_PROMPT_INSPIRATIONS.forEach { sample ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0xFF221736))
                                                .border(BorderStroke(1.dp, Color(0xFF4C2882)), RoundedCornerShape(14.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    aiPrompt = sample
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = sample,
                                                color = Color(0xFFE9D5FF),
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                if (aiErrorMessage != null) {
                                    Text(
                                        text = aiErrorMessage ?: "",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                // Generate Button
                                Button(
                                    onClick = {
                                        if (aiPrompt.isNotBlank() && !isGeneratingAi) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isGeneratingAi = true
                                            aiErrorMessage = null
                                            coroutineScope.launch {
                                                val res = viewModel.generateCatalogWithAi(aiPrompt)
                                                isGeneratingAi = false
                                                res.fold(
                                                    onSuccess = { generated ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        isAiPowered = true
                                                        catalogName = generated.title
                                                        mediaType = generated.filter.mediaType
                                                        personQuery = generated.filter.personQuery
                                                        studioQuery = generated.filter.studioQuery
                                                        selectedGenres = generated.filter.genres.toSet()
                                                        selectedLanguages = generated.filter.languages.toSet()
                                                        ratingFilterEnabled = generated.filter.ratingFilterEnabled
                                                        minRating = generated.filter.minRating
                                                        maxRating = generated.filter.maxRating
                                                        yearFilterEnabled = generated.filter.yearFilterEnabled
                                                        minYear = generated.filter.minYear
                                                        maxYear = generated.filter.maxYear
                                                        sortBy = generated.filter.sortBy
                                                        sortAscending = generated.filter.sortAscending
                                                        semanticKeywords = generated.filter.keywords
                                                        isAdvancedExpanded = true // reveal populated controls
                                                    },
                                                    onFailure = { err ->
                                                        aiErrorMessage = err.localizedMessage ?: "Generation failed"
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    enabled = aiPrompt.isNotBlank() && !isGeneratingAi,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFA855F7),
                                        disabledContainerColor = Color(0xFF332049)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isGeneratingAi) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Curating with AI...", color = Color.White, fontFamily = NdotFont, fontSize = 12.5.sp)
                                    } else {
                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Generate Parameters", color = Color.White, fontFamily = NdotFont, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Content Type Segmented Tabs
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CONTENT TYPE",
                        color = Color(0xFF9CA3AF),
                        fontFamily = NdotFont,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.6.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF16171B))
                            .border(BorderStroke(1.dp, Color(0xFF282A31)), RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("all" to "Both", "movie" to "Movies", "series" to "Series").forEach { (typeKey, label) ->
                            val isSelected = mediaType == typeKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFA855F7) else Color.Transparent)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        mediaType = typeKey
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFF9CA3AF),
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Section 4: Live Library Match Counter Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF181A22))
                        .border(BorderStroke(1.dp, Color(0xFF282C3A)), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (liveMatchingCount > 0) Color(0xFF2ECC71) else Color(0xFFE74C3C))
                        )
                        Text(
                            text = if (liveMatchingCount > 0) "$liveMatchingCount titles match in library" else "No matching titles currently in library",
                            color = Color(0xFFD1D5DB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Pin to Home Screen toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pinToHome = !pinToHome
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin to Home",
                            tint = if (pinToHome) Color(0xFFA855F7) else Color(0xFF6B7280),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (pinToHome) "Pinned" else "Pin",
                            color = if (pinToHome) Color(0xFFA855F7) else Color(0xFF6B7280),
                            fontFamily = NdotFont,
                            fontSize = 10.5.sp
                        )
                    }
                }

                // Section 5: "Advanced Controls" Expandable Accordion
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(BorderStroke(1.dp, Color(0xFF282A31)), RoundedCornerShape(18.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16171B)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isAdvancedExpanded = !isAdvancedExpanded
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFFA855F7),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Advanced Controls",
                                    color = Color.White,
                                    fontFamily = NdotFont,
                                    fontSize = 13.5.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Icon(
                                imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Collapsible Body
                        AnimatedVisibility(
                            visible = isAdvancedExpanded,
                            enter = expandVertically(animationSpec = spring(dampingRatio = 0.78f)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.78f)) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                // 1. Person Search (Actor / Director)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "PERSON (ACTOR / DIRECTOR)",
                                        color = Color(0xFF9CA3AF),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp
                                    )

                                    OutlinedTextField(
                                        value = personQuery,
                                        onValueChange = {
                                            personQuery = it
                                            selectedPersonId = null // clear id on edit
                                        },
                                        placeholder = { Text("e.g. Christopher Nolan, Leonardo DiCaprio", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF1B1C22),
                                            unfocusedContainerColor = Color(0xFF1B1C22),
                                            focusedBorderColor = Color(0xFFA855F7),
                                            unfocusedBorderColor = Color(0xFF282A31),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        trailingIcon = {
                                            if (isSearchingPeople) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFA855F7))
                                            } else if (personQuery.isNotBlank()) {
                                                IconButton(onClick = { personQuery = ""; selectedPersonId = null }) {
                                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    )

                                    // TMDB Person suggestions
                                    if (personSearchResults.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF20222A))
                                                .padding(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            personSearchResults.forEach { p ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            personQuery = p.name
                                                            selectedPersonId = p.id
                                                            personSearchResults = emptyList()
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = p.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                                    Text(text = p.department ?: "Crew", color = Color(0xFFA855F7), fontSize = 10.5.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                // 2. Studio / OTT Platform
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "STUDIO / NETWORK / PLATFORM",
                                        color = Color(0xFF9CA3AF),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp
                                    )

                                    OutlinedTextField(
                                        value = studioQuery,
                                        onValueChange = { studioQuery = it },
                                        placeholder = { Text("e.g. Studio Ghibli, HBO, Netflix, Warner Bros", color = Color(0xFF6B7280), fontSize = 12.5.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF1B1C22),
                                            unfocusedContainerColor = Color(0xFF1B1C22),
                                            focusedBorderColor = Color(0xFFA855F7),
                                            unfocusedBorderColor = Color(0xFF282A31),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )

                                    // Quick studio chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        STANDARD_STUDIOS.forEach { std ->
                                            val isSel = studioQuery.equals(std, ignoreCase = true)
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    studioQuery = if (isSel) "" else std
                                                },
                                                label = { Text(std, fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFA855F7),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF1F2027),
                                                    labelColor = Color(0xFFD1D5DB)
                                                )
                                            )
                                        }
                                    }
                                }

                                // 3. Genres Selection
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "GENRES",
                                        color = Color(0xFF9CA3AF),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp
                                    )

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        STANDARD_GENRES.forEach { genre ->
                                            val isSelected = selectedGenres.contains(genre)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedGenres = if (isSelected) selectedGenres - genre else selectedGenres + genre
                                                },
                                                label = { Text(genre, fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFA855F7),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF1F2027),
                                                    labelColor = Color(0xFFD1D5DB)
                                                )
                                            )
                                        }
                                    }
                                }

                                // 4. Languages / Audio
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "AUDIO & LANGUAGES",
                                        color = Color(0xFF9CA3AF),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        STANDARD_LANGUAGES.forEach { lang ->
                                            val isSel = selectedLanguages.contains(lang)
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    selectedLanguages = if (isSel) selectedLanguages - lang else selectedLanguages + lang
                                                },
                                                label = { Text(lang, fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFA855F7),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF1F2027),
                                                    labelColor = Color(0xFFD1D5DB)
                                                )
                                            )
                                        }
                                    }
                                }

                                // 5. Rating Range Toggle & Slider
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RATING FILTER (${String.format("%.1f", minRating)} - ${String.format("%.1f", maxRating)})",
                                            color = Color(0xFF9CA3AF),
                                            fontFamily = NdotFont,
                                            fontSize = 10.sp
                                        )

                                        Switch(
                                            checked = ratingFilterEnabled,
                                            onCheckedChange = { ratingFilterEnabled = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFFA855F7),
                                                uncheckedTrackColor = Color(0xFF282A31)
                                            )
                                        )
                                    }

                                    if (ratingFilterEnabled) {
                                        RangeSlider(
                                            value = minRating..maxRating,
                                            onValueChange = { range ->
                                                minRating = range.start
                                                maxRating = range.endInclusive
                                            },
                                            valueRange = 0f..10f,
                                            steps = 19,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFFA855F7),
                                                activeTrackColor = Color(0xFFA855F7),
                                                inactiveTrackColor = Color(0xFF2D2E36)
                                            )
                                        )
                                    }
                                }

                                // 6. Year Range Toggle & Slider
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "YEAR FILTER ($minYear - $maxYear)",
                                            color = Color(0xFF9CA3AF),
                                            fontFamily = NdotFont,
                                            fontSize = 10.sp
                                        )

                                        Switch(
                                            checked = yearFilterEnabled,
                                            onCheckedChange = { yearFilterEnabled = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFFA855F7),
                                                uncheckedTrackColor = Color(0xFF282A31)
                                            )
                                        )
                                    }

                                    if (yearFilterEnabled) {
                                        RangeSlider(
                                            value = minYear.toFloat()..maxYear.toFloat(),
                                            onValueChange = { range ->
                                                minYear = range.start.toInt()
                                                maxYear = range.endInclusive.toInt()
                                            },
                                            valueRange = 1970f..2026f,
                                            steps = 55,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFFA855F7),
                                                activeTrackColor = Color(0xFFA855F7),
                                                inactiveTrackColor = Color(0xFF2D2E36)
                                            )
                                        )
                                    }
                                }

                                // 7. Sort Options
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "SORT ORDER",
                                        color = Color(0xFF9CA3AF),
                                        fontFamily = NdotFont,
                                        fontSize = 10.sp
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CatalogSortBy.values().forEach { s ->
                                            val isSel = sortBy == s
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    if (isSel) sortAscending = !sortAscending
                                                    else {
                                                        sortBy = s
                                                        sortAscending = false
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        if (isSel) "${s.label} ${if (sortAscending) "↑" else "↓"}" else s.label,
                                                        fontSize = 11.sp
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFFA855F7),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF1F2027),
                                                    labelColor = Color(0xFFD1D5DB)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Dialog to prompt adding an API key if tapped when disabled
    if (showApiKeyPromptDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyPromptDialog = false },
            title = {
                Text(
                    text = "AI Key Required",
                    fontFamily = NdotFont,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "To build AI-powered categories, add your free Google Gemini API key or OpenRouter API key in Settings.",
                    color = Color(0xFFD1D5DB),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApiKeyPromptDialog = false
                        onDismiss()
                        viewModel.selectTab(AppTab.SETTINGS)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Go to Settings →", color = Color.White, fontFamily = NdotFont, fontSize = 12.5.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyPromptDialog = false }) {
                    Text("Not Now", color = Color(0xFF9CA3AF), fontSize = 12.5.sp)
                }
            },
            containerColor = Color(0xFF1E1F24),
            shape = RoundedCornerShape(20.dp)
        )
    }
}
