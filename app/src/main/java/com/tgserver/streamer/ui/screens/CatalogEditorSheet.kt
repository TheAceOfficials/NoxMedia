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
    "Action", "Adventure", "Animation", "Anime", "Comedy", "Crime",
    "Documentary", "Drama", "Family", "Fantasy", "History", "Horror",
    "Music", "Mystery", "Murder", "Romance", "Science Fiction", "Thriller",
    "Time Travel", "War", "Western"
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
    var selectedPersonName by remember { mutableStateOf(initialFilter.personQuery) }
    var selectedPersonProfilePath by remember { mutableStateOf(initialFilter.personProfilePath) }

    var studioQuery by remember { mutableStateOf(initialFilter.studioQuery) }
    var selectedStudioId by remember { mutableStateOf(initialFilter.studioTmdbId) }
    var selectedStudioName by remember { mutableStateOf(initialFilter.studioQuery) }
    var selectedStudioLogoPath by remember { mutableStateOf(initialFilter.studioLogoPath) }

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

    // Dynamic library languages
    val dynamicLanguages = remember(mediaGroups) {
        val langs = mutableSetOf<String>()
        mediaGroups.forEach { g ->
            if (g.hasHindi) langs.add("Hindi")
            if (g.hasEnglish) langs.add("English")
            if (g.hasJapanese) langs.add("Japanese")
            if (g.hasDualAudio) langs.add("Dual Audio")
            if (g.hasMultiAudio) langs.add("Multi Dub")

            g.items.take(8).forEach { item ->
                val fn = item.fileName.lowercase()
                if (fn.contains("tamil") || fn.contains(".tam.")) langs.add("Tamil")
                if (fn.contains("telugu") || fn.contains(".tel.")) langs.add("Telugu")
                if (fn.contains("malayalam") || fn.contains(".mal.")) langs.add("Malayalam")
                if (fn.contains("kannada") || fn.contains(".kan.")) langs.add("Kannada")
                if (fn.contains("bengali") || fn.contains(".ben.")) langs.add("Bengali")
                if (fn.contains("marathi") || fn.contains(".mar.")) langs.add("Marathi")
                if (fn.contains("korean") || fn.contains(".kor.")) langs.add("Korean")
                if (fn.contains("spanish") || fn.contains(".spa.")) langs.add("Spanish")
                if (fn.contains("french") || fn.contains(".fre.")) langs.add("French")
                if (fn.contains("german") || fn.contains(".ger.")) langs.add("German")
                if (fn.contains("italian") || fn.contains(".ita.")) langs.add("Italian")
                if (fn.contains("russian") || fn.contains(".rus.")) langs.add("Russian")
                if (fn.contains("chinese") || fn.contains(".chi.")) langs.add("Chinese")
            }
        }
        val sortedLangs = langs.toList().sorted()
        listOf("Any") + (if (sortedLangs.isNotEmpty()) sortedLangs else listOf("Hindi", "English", "Japanese", "Multi Dub", "Dual Audio"))
    }

    // Person search results
    var personSearchResults by remember { mutableStateOf<List<TmdbPersonItem>>(emptyList()) }
    var isSearchingPeople by remember { mutableStateOf(false) }

    // Debounced TMDB / TVDB / Curated Person Search
    LaunchedEffect(personQuery) {
        val q = personQuery.trim()
        if (q.length >= 2 && selectedPersonName != q) {
            delay(250)
            isSearchingPeople = true
            personSearchResults = viewModel.searchPeople(q)
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

    // Studio search results
    var studioSearchResults by remember { mutableStateOf<List<TmdbCompanyItem>>(emptyList()) }
    var isSearchingStudio by remember { mutableStateOf(false) }

    // Debounced TMDB / TVDB / Curated Studio Search
    LaunchedEffect(studioQuery) {
        val q = studioQuery.trim()
        if (q.length >= 2 && selectedStudioName != q) {
            delay(250)
            isSearchingStudio = true
            studioSearchResults = viewModel.searchCompanies(q)
            isSearchingStudio = false
        } else {
            studioSearchResults = emptyList()
        }
    }

    // Company Filmography IDs
    var companyFilmographyIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    LaunchedEffect(selectedStudioId) {
        val id = selectedStudioId
        if (id != null && id > 0) {
            companyFilmographyIds = viewModel.getCompanyFilmographyIds(id)
        } else {
            companyFilmographyIds = emptySet()
        }
    }

    // Live preview of matching items in library
    val liveMatchingCount = remember(
        mediaGroups, mediaType, selectedPersonName, selectedPersonId, personFilmographyIds,
        selectedStudioName, selectedStudioId, companyFilmographyIds,
        selectedGenres, selectedLanguages,
        ratingFilterEnabled, minRating, maxRating,
        yearFilterEnabled, minYear, maxYear,
        sortBy, sortAscending, semanticKeywords
    ) {
        val effectiveLangs = if (selectedLanguages.contains("Any")) emptyList() else selectedLanguages.toList()
        val tempFilter = CatalogFilterCriteria(
            mediaType = mediaType,
            personQuery = selectedPersonName,
            personTmdbId = selectedPersonId,
            personProfilePath = selectedPersonProfilePath,
            studioQuery = selectedStudioName,
            studioTmdbId = selectedStudioId,
            studioLogoPath = selectedStudioLogoPath,
            genres = selectedGenres.toList(),
            languages = effectiveLangs,
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
        val tempCatalog = CatalogDefinition(name = "Preview", isAiPowered = isAiPowered, filter = tempFilter)
        viewModel.filterMediaForCatalog(tempCatalog, personFilmographyIds, companyFilmographyIds).size
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
                                if (selectedPersonName.isNotBlank()) "Films by $selectedPersonName"
                                else if (selectedStudioName.isNotBlank()) "$selectedStudioName Collection"
                                else if (selectedGenres.isNotEmpty()) "${selectedGenres.joinToString(" + ")} Spotlight"
                                else "Custom Catalog"
                            }
                            val effectiveLangs = if (selectedLanguages.contains("Any")) emptyList() else selectedLanguages.toList()
                            val finalFilter = CatalogFilterCriteria(
                                mediaType = mediaType,
                                personQuery = selectedPersonName.trim(),
                                personTmdbId = selectedPersonId,
                                personProfilePath = selectedPersonProfilePath,
                                studioQuery = selectedStudioName.trim(),
                                studioTmdbId = selectedStudioId,
                                studioLogoPath = selectedStudioLogoPath,
                                genres = selectedGenres.toList(),
                                languages = effectiveLangs,
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
                        modifier = Modifier.weight(1f, fill = false),
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
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Pin to Home Screen toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pinToHome = !pinToHome
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
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
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false
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

                                        if (selectedPersonName.isNotBlank()) {
                                            // Tagged Selected Person Card
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF201830))
                                                    .border(BorderStroke(1.dp, Color(0xFF5B21B6)), RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    if (!selectedPersonProfilePath.isNullOrBlank()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .border(BorderStroke(1.dp, Color(0xFFA855F7)), CircleShape)
                                                        ) {
                                                            RemoteImage(
                                                                url = if (selectedPersonProfilePath!!.startsWith("http")) selectedPersonProfilePath else "https://image.tmdb.org/t/p/w185$selectedPersonProfilePath",
                                                                contentDescription = selectedPersonName,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF382054)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Person,
                                                                contentDescription = null,
                                                                tint = Color(0xFFE9D5FF),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }

                                                    Column {
                                                        Text(
                                                            text = selectedPersonName,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Tagged Person",
                                                            color = Color(0xFFA855F7),
                                                            fontFamily = NdotFont,
                                                            fontSize = 9.5.sp
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedPersonId = null
                                                        selectedPersonName = ""
                                                        selectedPersonProfilePath = null
                                                        personQuery = ""
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove Tag",
                                                        tint = Color(0xFF9CA3AF),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Search Input
                                            OutlinedTextField(
                                                value = personQuery,
                                                onValueChange = {
                                                    personQuery = it
                                                    if (selectedPersonName.isNotBlank() && it != selectedPersonName) {
                                                        selectedPersonName = ""
                                                        selectedPersonId = null
                                                        selectedPersonProfilePath = null
                                                    }
                                                },
                                                placeholder = { Text("Search actor/director (e.g. Christopher Nolan)", color = Color(0xFF6B7280), fontSize = 12.sp) },
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
                                                        IconButton(onClick = { personQuery = "" }) {
                                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            )

                                            // TMDB Person suggestions with photo
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
                                                                    selectedPersonName = p.name
                                                                    selectedPersonId = p.id
                                                                    selectedPersonProfilePath = p.profilePath
                                                                    personQuery = p.name
                                                                    personSearchResults = emptyList()
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                if (!p.profilePath.isNullOrBlank()) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(28.dp)
                                                                            .clip(CircleShape)
                                                                    ) {
                                                                        RemoteImage(
                                                                            url = p.fullProfileUrl,
                                                                            contentDescription = p.name,
                                                                            modifier = Modifier.fillMaxSize()
                                                                        )
                                                                    }
                                                                } else {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(28.dp)
                                                                            .clip(CircleShape)
                                                                            .background(Color(0xFF2D2E38)),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                                    }
                                                                }
                                                                Text(text = p.name, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
                                                            }
                                                            Text(text = p.department ?: "Actor / Crew", color = Color(0xFFA855F7), fontSize = 10.5.sp)
                                                        }
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

                                        if (selectedStudioName.isNotBlank()) {
                                            // Tagged Selected Studio Card
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFF201830))
                                                    .border(BorderStroke(1.dp, Color(0xFF5B21B6)), RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    if (!selectedStudioLogoPath.isNullOrBlank()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Color(0xFF16171B))
                                                                .padding(4.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            RemoteImage(
                                                                url = if (selectedStudioLogoPath!!.startsWith("http")) selectedStudioLogoPath else "https://image.tmdb.org/t/p/w185$selectedStudioLogoPath",
                                                                contentDescription = selectedStudioName,
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF382054)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Business,
                                                                contentDescription = null,
                                                                tint = Color(0xFFE9D5FF),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }

                                                    Column {
                                                        Text(
                                                            text = selectedStudioName,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "Tagged Studio / Network",
                                                            color = Color(0xFFA855F7),
                                                            fontFamily = NdotFont,
                                                            fontSize = 9.5.sp
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedStudioId = null
                                                        selectedStudioName = ""
                                                        selectedStudioLogoPath = null
                                                        studioQuery = ""
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove Tag",
                                                        tint = Color(0xFF9CA3AF),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Search Input
                                            OutlinedTextField(
                                                value = studioQuery,
                                                onValueChange = {
                                                    studioQuery = it
                                                    if (selectedStudioName.isNotBlank() && it != selectedStudioName) {
                                                        selectedStudioName = ""
                                                        selectedStudioId = null
                                                        selectedStudioLogoPath = null
                                                    }
                                                },
                                                placeholder = { Text("Search studio (e.g. HBO, Warner Bros, A24, Ghibli)", color = Color(0xFF6B7280), fontSize = 12.sp) },
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
                                                    if (isSearchingStudio) {
                                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFA855F7))
                                                    } else if (studioQuery.isNotBlank()) {
                                                        IconButton(onClick = { studioQuery = "" }) {
                                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            )

                                            // Live TMDB Studio suggestions
                                            if (studioSearchResults.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0xFF20222A))
                                                        .padding(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    studioSearchResults.forEach { c ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    selectedStudioName = c.name
                                                                    selectedStudioId = c.id
                                                                    selectedStudioLogoPath = c.logoPath
                                                                    studioQuery = c.name
                                                                    studioSearchResults = emptyList()
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                if (!c.logoPath.isNullOrBlank()) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(26.dp)
                                                                            .clip(RoundedCornerShape(4.dp))
                                                                            .background(Color(0xFF16171B))
                                                                            .padding(2.dp),
                                                                        contentAlignment = Alignment.Center
                                                                    ) {
                                                                        RemoteImage(
                                                                            url = c.fullLogoUrl,
                                                                            contentDescription = c.name,
                                                                            modifier = Modifier.fillMaxSize()
                                                                        )
                                                                    }
                                                                } else {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Business,
                                                                        contentDescription = null,
                                                                        tint = Color(0xFFA855F7),
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                                Text(
                                                                    text = c.name,
                                                                    color = Color.White,
                                                                    fontSize = 12.5.sp,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                            Text(
                                                                text = "Studio",
                                                                color = Color(0xFFA855F7),
                                                                fontSize = 10.5.sp
                                                            )
                                                        }
                                                    }
                                                }
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

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val isAnySelected = selectedLanguages.isEmpty() || selectedLanguages.contains("Any")
                                        dynamicLanguages.forEach { lang ->
                                            val isSel = if (lang == "Any") isAnySelected else (!isAnySelected && selectedLanguages.contains(lang))
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (lang == "Any") {
                                                        selectedLanguages = emptySet()
                                                    } else {
                                                        if (selectedLanguages.contains(lang)) {
                                                            val updated = selectedLanguages - lang
                                                            selectedLanguages = updated
                                                        } else {
                                                            selectedLanguages = (selectedLanguages - "Any") + lang
                                                        }
                                                    }
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
