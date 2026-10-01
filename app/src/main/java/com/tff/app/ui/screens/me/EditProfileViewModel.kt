package com.tff.app.ui.screens.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.core.AppClock
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.AvailabilityPattern
import com.tff.app.model.City
import com.tff.app.model.CompMode
import com.tff.app.model.ContentFlag
import com.tff.app.model.Format
import com.tff.app.model.Goal
import com.tff.app.model.Level
import com.tff.app.model.LinkKind
import com.tff.app.model.Perk
import com.tff.app.model.Profile
import com.tff.app.model.RoleCode
import com.tff.app.model.WorkLink
import com.tff.app.ui.screens.onboarding.ProfileLogic
import com.tff.app.ui.screens.onboarding.RoleSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One work link being edited (url text kept raw until save). */
data class LinkDraft(
    val key: Long,
    val id: String,
    val url: String,
    val kind: LinkKind,
    val roleCode: RoleCode?,
    val title: String,
    val year: String,
) {
    val urlOk: Boolean get() = ProfileLogic.normaliseUrl(url) != null
    val yearOk: Boolean get() = year.isBlank() || year.toIntOrNull()?.let { it in 1950..2100 } == true
}

/** The whole S20 form. Profile fields the form doesn't touch are carried in [base]. */
data class EditForm(
    val base: Profile,
    val name: String,
    val pronouns: String,
    val bio: String,
    val affiliations: List<String>,
    val roles: RoleSelection,
    val level: Level,
    val cityId: String,
    val areaId: String?,
    val radiusKm: Int,
    val travelIfCovered: Boolean,
    val remoteOk: Boolean,
    val languages: Set<String>,
    val availability: AvailabilityPattern,
    val busy: Set<LocalDate>,
    val compMode: CompMode,
    val mustHaves: Set<Perk>,
    val goals: Set<Goal>,
    val wontDo: Set<ContentFlag>,
    val minRate: String,
    val kitFee: String,
    val links: List<LinkDraft>,
    val gear: List<String>,
    val tools: List<String>,
    val films: List<String>,
    val genres: Set<String>,
    val formats: Set<Format>,
) {
    val nameOk get() = name.trim().length in 2..40
    val bioOk get() = bio.length <= 280
    val rateOk get() = ProfileLogic.parseRate(minRate) != -1
    val kitOk get() = ProfileLogic.parseRate(kitFee) != -1
    val linksOk get() = links.all { it.urlOk && it.yearOk }
    val valid get() = nameOk && bioOk && rateOk && kitOk && linksOk && languages.isNotEmpty() && roles.primary != null && areaId != null

    fun toProfile(): Profile = base.copy(
        displayName = name.trim(),
        pronouns = pronouns.trim().ifBlank { null },
        bio = bio.trim().ifBlank { null },
        affiliations = affiliations,
        primaryRole = roles.primary ?: base.primaryRole,
        secondaryRoles = roles.secondaryRoles(base.secondaryRoles),
        stepUpRoles = roles.stepUp,
        level = level,
        cityId = cityId,
        areaId = areaId ?: base.areaId,
        travelRadiusKm = radiusKm,
        travelIfCovered = travelIfCovered,
        remoteOk = remoteOk,
        languages = languages.toList(),
        availabilityPattern = availability,
        busyDates = busy,
        compMode = compMode,
        unpaidMustHaves = if (compMode == CompMode.PAID_ONLY) emptyList() else ProfileLogic.unpaidPerkOptions.filter { it in mustHaves },
        goals = Goal.entries.filter { it in goals },
        contentWontDo = ContentFlag.entries.filter { it in wontDo },
        kitFeeDay = ProfileLogic.parseRate(kitFee)?.takeIf { it > 0 },
        workLinks = links.map { d ->
            WorkLink(id = d.id, url = ProfileLogic.normaliseUrl(d.url) ?: d.url.trim(), kind = d.kind, roleCode = d.roleCode,
                title = d.title.trim().ifBlank { null }, year = d.year.toIntOrNull())
        },
        gear = gear,
        tools = tools,
        tasteFilms = films.take(5),
        genresPref = genres.toList(),
        formatsPref = Format.entries.filter { it in formats },
    )

    companion object {
        fun from(p: Profile, minRate: Int?): EditForm = EditForm(
            base = p, name = p.displayName, pronouns = p.pronouns.orEmpty(), bio = p.bio.orEmpty(), affiliations = p.affiliations,
            roles = RoleSelection(p.primaryRole, p.secondaryRoles.map { it.code }, p.stepUpRoles), level = p.level,
            cityId = p.cityId, areaId = p.areaId, radiusKm = p.travelRadiusKm, travelIfCovered = p.travelIfCovered, remoteOk = p.remoteOk,
            languages = p.languages.toCollection(LinkedHashSet()), availability = p.availabilityPattern, busy = p.busyDates,
            compMode = p.compMode, mustHaves = p.unpaidMustHaves.toSet(), goals = p.goals.toSet(), wontDo = p.contentWontDo.toSet(),
            minRate = minRate?.toString().orEmpty(), kitFee = p.kitFeeDay?.toString().orEmpty(),
            links = p.workLinks.mapIndexed { i, l -> LinkDraft(i.toLong(), l.id, l.url, l.kind, l.roleCode, l.title.orEmpty(), l.year?.toString().orEmpty()) },
            gear = p.gear, tools = p.tools, films = p.tasteFilms, genres = p.genresPref.toSet(), formats = p.formatsPref.toSet(),
        )
    }
}

sealed interface EditUi {
    data object Loading : EditUi
    data class Error(val message: String?) : EditUi
    data object NoProfile : EditUi
    data class Ready(
        val form: EditForm,
        val original: Profile,
        val originalRate: Int?,
        val cities: List<City>,
        val saving: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val showErrors: Boolean = false,
    ) : EditUi {
        val city: City? get() = cities.firstOrNull { it.id == form.cityId }
        /** Compared form-to-form, so re-ordering done by toProfile() never counts as a change. */
        val dirty: Boolean get() = form != EditForm.from(original, originalRate)
    }
}

/** S20 Edit profile: a working copy of my profile; [save] runs Repo.saveProfile (with the private rate). */
class EditProfileViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow<EditUi>(EditUi.Loading)
    val ui: StateFlow<EditUi> = _ui
    private var nextKey = 1000L
    val today: LocalDate get() = AppClock.today()

    init { load() }

    fun load() {
        _ui.value = EditUi.Loading
        viewModelScope.launch {
            runCatching {
                val me = repo.me().first() ?: return@runCatching null
                val rate = repo.myPrivateMinRate()
                val cities = repo.cities()
                EditUi.Ready(EditForm.from(me, rate), me, rate, cities)
            }.onSuccess { _ui.value = it ?: EditUi.NoProfile }
                .onFailure { e -> _ui.value = EditUi.Error((e as? RepoException)?.userMessage) }
        }
    }

    fun edit(f: (EditForm) -> EditForm) = _ui.update { s -> if (s is EditUi.Ready) s.copy(form = f(s.form), error = null) else s }

    fun pickCity(c: City) = edit { it.copy(cityId = c.id, areaId = if (c.areas.size == 1) c.areas.first().id else it.areaId.takeIf { a -> c.areas.any { x -> x.id == a } }) }

    fun addLink() = edit { if (it.links.size >= MAX_LINKS) it else it.copy(links = it.links + LinkDraft(nextKey++, "", "", LinkKind.REEL, it.roles.primary, "", "")) }
    fun updateLink(key: Long, f: (LinkDraft) -> LinkDraft) = edit { form -> form.copy(links = form.links.map { if (it.key == key) f(it) else it }) }
    fun removeLink(key: Long) = edit { form -> form.copy(links = form.links.filterNot { it.key == key }) }
    fun moveLink(key: Long, by: Int) = edit { form ->
        val i = form.links.indexOfFirst { it.key == key }
        val j = i + by
        if (i < 0 || j !in form.links.indices) form else form.copy(links = form.links.toMutableList().also { l -> val t = l[i]; l[i] = l[j]; l[j] = t })
    }

    fun toggleBusy(d: LocalDate) = edit { it.copy(busy = if (d in it.busy) it.busy - d else it.busy + d) }

    fun save(fallback: String) {
        val s = _ui.value as? EditUi.Ready ?: return
        if (s.saving) return
        if (!s.form.valid) { _ui.value = s.copy(showErrors = true); return }
        val p = s.form.toProfile()
        val rate = ProfileLogic.parseRate(s.form.minRate)
        _ui.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            val r = runCatching { repo.saveProfile(p, rate) }.getOrElse { Result.failure(it) }
            r.onSuccess { _ui.update { st -> if (st is EditUi.Ready) st.copy(saving = false, saved = true) else st } }
                .onFailure { e -> _ui.update { st -> if (st is EditUi.Ready) st.copy(saving = false, error = (e as? RepoException)?.userMessage ?: fallback) else st } }
        }
    }

    companion object { const val MAX_LINKS = 8 }
}
