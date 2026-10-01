package com.tff.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tff.app.data.Repo
import com.tff.app.data.RepoException
import com.tff.app.model.AvailabilityPattern
import com.tff.app.model.City
import com.tff.app.model.CompMode
import com.tff.app.model.Density
import com.tff.app.model.Level
import com.tff.app.model.LinkKind
import com.tff.app.model.Perk
import com.tff.app.model.Profile
import com.tff.app.model.RoleWithProject
import com.tff.app.model.WorkLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the 3 onboarding steps collect (S04). */
data class OnboardingForm(
    val name: String = "",
    val roles: RoleSelection = RoleSelection(),
    val level: Level? = null,
    val cityId: String? = null,
    val areaId: String? = null,
    val radiusKm: Int = 15,
    val remoteOk: Boolean = false,
    val remoteTouched: Boolean = false,
    val languages: Set<String> = setOf("en"),
    val compMode: CompMode? = null,
    val mustHaves: Set<Perk> = emptySet(),
    val availability: AvailabilityPattern? = null,
    val minRate: String = "",
    val link: String = "",
    val linkKind: LinkKind? = null,          // null = auto-detected from the URL
) {
    val nameOk: Boolean get() = name.trim().length in 2..40
    val step1Ok: Boolean get() = nameOk && roles.primary != null && level != null
    val step2Ok: Boolean get() = cityId != null && areaId != null && languages.isNotEmpty()
    val rateValue: Int? get() = ProfileLogic.parseRate(minRate)
    val rateOk: Boolean get() = rateValue != -1
    val linkUrl: String? get() = link.trim().takeIf { it.isNotEmpty() }?.let { ProfileLogic.normaliseUrl(it) }
    val linkOk: Boolean get() = link.isBlank() || linkUrl != null
    val effectiveLinkKind: LinkKind get() = linkKind ?: ProfileLogic.detectLinkKind(link)
    val step3Ok: Boolean get() = rateOk && linkOk
}

data class OnboardingUi(
    val step: Int = 1,
    val form: OnboardingForm = OnboardingForm(),
    val cities: List<City> = emptyList(),
    val citiesError: Boolean = false,
    val density: Density? = null,
    val nearby: ProfileLogic.Nearby? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    val toast: String? = null,
) {
    val city: City? get() = cities.firstOrNull { it.id == form.cityId }
}

class OnboardingViewModel(private val repo: Repo) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui
    private var densityJob: Job? = null
    private var candidates: List<RoleWithProject> = emptyList()
    private val startedAt = System.currentTimeMillis()

    init { loadCities() }

    fun loadCities() {
        _ui.update { it.copy(citiesError = false) }
        viewModelScope.launch {
            runCatching { repo.cities() }
                .onSuccess { list -> _ui.update { it.copy(cities = list) } }
                .onFailure { _ui.update { it.copy(citiesError = true) } }
        }
    }

    private fun form(f: (OnboardingForm) -> OnboardingForm) = _ui.update { it.copy(form = f(it.form), error = null) }

    fun setName(v: String) = form { it.copy(name = v.take(40)) }
    fun setRoles(sel: RoleSelection) = form {
        val remote = if (it.remoteTouched) it.remoteOk else ProfileLogic.remoteDefault(sel.primary)
        it.copy(roles = sel, remoteOk = remote)
    }
    fun setLevel(l: Level) = form { it.copy(level = l) }

    fun pickCity(c: City) {
        form { it.copy(cityId = c.id, areaId = if (c.areas.size == 1) c.areas.first().id else it.areaId.takeIf { a -> c.areas.any { x -> x.id == a } }) }
        densityJob?.cancel()
        candidates = emptyList()
        _ui.update { it.copy(density = null, nearby = null) }
        densityJob = viewModelScope.launch {
            launch { runCatching { candidates = repo.discoverCandidates(c.id); recount() } }
            runCatching { repo.density(c.id).collect { d -> _ui.update { it.copy(density = d) }; recount() } }
        }
    }

    fun missingCity(name: String, thanks: String) {
        if (name.isBlank()) return
        viewModelScope.launch { runCatching { repo.log("city_request", mapOf("city" to name)) } }
        _ui.update { it.copy(toast = thanks) }
    }

    fun toastShown() = _ui.update { it.copy(toast = null) }

    fun pickArea(id: String) { form { it.copy(areaId = id) }; recount() }
    fun setRadius(km: Int) { form { it.copy(radiusKm = km.coerceIn(2, 50)) }; recount() }
    fun setRemote(on: Boolean) = form { it.copy(remoteOk = on, remoteTouched = true) }
    fun toggleLanguage(code: String) = form { it.copy(languages = if (code in it.languages) it.languages - code else it.languages + code) }
    fun setComp(m: CompMode) = form { it.copy(compMode = m) }
    fun togglePerk(p: Perk) = form { it.copy(mustHaves = if (p in it.mustHaves) it.mustHaves - p else it.mustHaves + p) }
    fun setAvailability(a: AvailabilityPattern) = form { it.copy(availability = a) }
    fun setRate(v: String) = form { it.copy(minRate = v.filter { ch -> ch.isDigit() || ch == ',' }.take(9)) }
    fun setLink(v: String) = form { it.copy(link = v.trim().take(300)) }
    fun setLinkKind(k: LinkKind) = form { it.copy(linkKind = k) }

    private fun recount() {
        val s = _ui.value
        val city = s.city ?: return
        val n = ProfileLogic.nearby(city, s.form.areaId, s.form.radiusKm, s.density, candidates)
        _ui.update { it.copy(nearby = n) }
    }

    fun next() {
        val s = _ui.value
        val ok = when (s.step) { 1 -> s.form.step1Ok; 2 -> s.form.step2Ok; else -> false }
        if (ok) _ui.update { it.copy(step = it.step + 1, error = null) }
    }

    fun back(): Boolean {
        val s = _ui.value
        if (s.step <= 1) return false
        _ui.update { it.copy(step = it.step - 1, error = null) }
        return true
    }

    /** [skip] = "Skip for now" on step 3: defaults applied (PRD F2). */
    fun finish(skip: Boolean, fallbackError: String) {
        val s = _ui.value
        val f = s.form
        if (s.saving || !f.step1Ok || !f.step2Ok) return
        if (!skip && !f.step3Ok) return
        val uid = repo.session.value?.userId ?: run { _ui.update { it.copy(error = fallbackError) }; return }
        val comp = if (skip) ProfileLogic.defaultCompMode(f.level) else f.compMode ?: ProfileLogic.defaultCompMode(f.level)
        val link = if (skip) null else f.linkUrl
        val profile = Profile(
            id = uid,
            displayName = f.name.trim(),
            primaryRole = f.roles.primary!!,
            secondaryRoles = f.roles.secondaryRoles(),
            stepUpRoles = f.roles.stepUp,
            level = f.level!!,
            cityId = f.cityId!!,
            areaId = f.areaId!!,
            travelRadiusKm = f.radiusKm,
            remoteOk = f.remoteOk,
            languages = f.languages.toList(),
            availabilityPattern = if (skip) AvailabilityPattern.ANY_DAY else f.availability ?: AvailabilityPattern.ANY_DAY,
            compMode = comp,
            unpaidMustHaves = if (skip || comp == CompMode.PAID_ONLY) emptyList() else ProfileLogic.unpaidPerkOptions.filter { it in f.mustHaves },
            goals = ProfileLogic.defaultGoals(f.level, comp, f.roles.stepUp),
            workLinks = link?.let { listOf(WorkLink(id = "", url = it, kind = f.effectiveLinkKind, roleCode = f.roles.primary)) }.orEmpty(),
            emailVerified = true,
            adultConfirmed = true,
        )
        val rate = if (skip) null else f.rateValue
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val r = runCatching { repo.saveProfile(profile, rate) }.getOrElse { Result.failure(it) }
            r.onSuccess {
                val secs = (System.currentTimeMillis() - startedAt) / 1000
                runCatching {
                    repo.log("onboarding_done", mapOf("seconds" to secs.toString(), "skipped_step3" to skip.toString(), "city" to profile.cityId, "role" to profile.primaryRole.name))
                    if (s.city?.isEarly == true) repo.log("early_city_signup", mapOf("city" to profile.cityId))
                }
                _ui.update { it.copy(saving = false, done = true) }
            }.onFailure { e -> _ui.update { it.copy(saving = false, error = (e as? RepoException)?.userMessage ?: fallbackError) } }
        }
    }
}
