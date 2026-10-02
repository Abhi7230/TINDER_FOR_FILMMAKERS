package com.tff.app.ui.screens.shootday

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tff.app.R
import com.tff.app.core.AppClock
import com.tff.app.core.Dates
import com.tff.app.di.AppContainer
import com.tff.app.di.rememberVm
import com.tff.app.domain.CardText
import com.tff.app.domain.ShareText
import com.tff.app.model.MatchState
import com.tff.app.model.Project
import com.tff.app.model.ShootDay
import com.tff.app.model.WithdrawReason
import com.tff.app.nav.AppNavigator
import com.tff.app.ui.components.CallTimeDisplay
import com.tff.app.ui.components.ChipTone
import com.tff.app.ui.components.CtChip
import com.tff.app.ui.components.CtTopBar
import com.tff.app.ui.components.EmptyState
import com.tff.app.ui.components.ErrorState
import com.tff.app.ui.components.GhostButton
import com.tff.app.ui.components.Monogram
import com.tff.app.ui.components.PrimaryButton
import com.tff.app.ui.components.SecondaryButton
import com.tff.app.ui.components.SectionHeader
import com.tff.app.ui.components.SprocketDivider
import com.tff.app.ui.components.TimecodeText
import com.tff.app.ui.components.TopBarAction
import com.tff.app.ui.screens.booking.WithdrawSheet
import com.tff.app.ui.screens.chat.Load
import com.tff.app.ui.screens.chat.clock
import com.tff.app.ui.screens.chat.userMessage
import com.tff.app.ui.theme.Ct
import com.tff.app.ui.theme.CtShapes
import java.time.LocalTime

/** S16V Shoot day view (team). Route shootday/{projectId}/{dayId}. */
@Composable
fun ShootDayViewScreen(nav: AppNavigator, projectId: String, shootDayId: String) {
    val vm = rememberVm(key = "day_${projectId}_$shootDayId") { repo -> ShootDayViewModel(repo, projectId, shootDayId) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        Load.Loading -> Frame(nav, null, null) { com.tff.app.ui.screens.chat.Ws6DetailSkeleton() }
        is Load.Failed -> Frame(nav, null, null) { ErrorState(onRetry = vm::retry, modifier = Modifier.fillMaxSize(), message = s.message) }
        is Load.Ready -> {
            val d = s.data
            if (d.project == null || d.day == null) Frame(nav, null, d.project?.project?.title) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(stringResource(R.string.ws6_day_missing_title), stringResource(R.string.ws6_day_missing_body), icon = Icons.Rounded.EventBusy) {
                        PrimaryButton(stringResource(R.string.ws6_back), { nav.back() })
                    }
                }
            } else DayContent(nav, vm, d)
        }
    }
}

@Composable
private fun Frame(nav: AppNavigator, title: String?, subtitle: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ct.colors.bg)) {
        CtTopBar(title ?: stringResource(R.string.ws6_day_title), subtitle = subtitle, onBack = nav::back)
        Box(Modifier.weight(1f).windowInsetsPadding(WindowInsets.navigationBars)) { content() }
    }
}

@Composable
private fun DayContent(nav: AppNavigator, vm: ShootDayViewModel, d: DayData) {
    val c = Ct.colors
    val context = LocalContext.current
    val res = context.resources
    val generic = stringResource(R.string.ws6_generic_error)
    val pf = d.project!!
    val full = d.day!!
    val day = full.day
    val project = pf.project
    val isOwner = pf.isOwner
    val myCall = full.myCall
    val callTime: LocalTime = myCall?.callTime ?: day.generalCall
    val busy by vm.busy.collectAsStateWithLifecycle()
    var withdraw by remember { mutableStateOf(false) }
    val today = AppClock.today()
    val passed = day.day.isBefore(today)
    val cancelled = day.cancelledAt != null
    val callAt = day.day.atTime(callTime).atZone(AppClock.zone).toInstant().toEpochMilli()
    val untilCall = callAt - AppClock.nowMillis()
    val stillComing = !isOwner && myCall != null && !cancelled && untilCall in 1..(48L * 3600_000) &&
        myCall.attendance == null && d.myMatch?.match?.state == MatchState.BOOKED && vm.features.withdrawRelease
    // "Day 1 of 2" only on multi-day shoots; the date sits under the call time once.
    val subtitle = if (full.dayCount > 1) stringResource(R.string.ws6e_day_subtitle, full.dayIndex, full.dayCount, Dates.short(day.day)) else Dates.short(day.day)
    val caption = Dates.shortUpper(day.day)
    val lateWrap = com.tff.app.ui.screens.booking.BookingCopy.isLateWrap(day)

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CtTopBar(
                title = project.title,
                subtitle = subtitle,
                onBack = nav::back,
                actions = {
                    if (isOwner) TopBarAction(Icons.Rounded.Edit, stringResource(R.string.ws6_day_edit), { nav.toShootDayEditor(project.id, day.id) })
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            SprocketDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                if (cancelled) {
                    Banner(Icons.Rounded.EventBusy, stringResource(R.string.ws6_day_cancelled), danger = true)
                    Spacer(Modifier.height(12.dp))
                } else if (passed) {
                    Banner(Icons.Rounded.CheckCircle, stringResource(R.string.ws6_day_passed), danger = false)
                    Spacer(Modifier.height(12.dp))
                }
                if (stillComing) {
                    // Inside T-48h this is the one primary action: "Yes" also records Got it.
                    StillComingCard(
                        call = "${Dates.shortUpper(day.day)} · ${Dates.time(callTime)}",
                        busy = busy,
                        alsoAcks = myCall?.ackAt == null,
                        onYes = {
                            vm.stillComing(alsoAck = myCall?.ackAt == null) { r ->
                                r.onSuccess { nav.toast(res.getString(R.string.ws6_still_yes_toast)) }.onFailure { nav.toast(it.userMessage() ?: generic) }
                            }
                        },
                        onCant = { withdraw = true },
                    )
                    Spacer(Modifier.height(16.dp))
                }
                CallTimeDisplay(
                    label = stringResource(if (isOwner || myCall == null) R.string.ws6_day_general_call else R.string.ws6_day_your_call),
                    time = callTime,
                    caption = caption,
                )
                Spacer(Modifier.height(10.dp))
                TimecodeText(
                    stringResource(R.string.ws6_day_general_wrap, Dates.time(day.generalCall), Dates.time(day.wrapBy)),
                    style = Ct.type.mono, color = c.textSecondary, maxLines = 2,
                )
                if (!isOwner && myCall != null && !cancelled && !(stillComing && myCall.ackAt == null)) {
                    Spacer(Modifier.height(16.dp))
                    if (myCall.ackAt == null) {
                        PrimaryButton(stringResource(R.string.ws6_got_it), {
                            vm.ack { r -> r.onSuccess { nav.toast(res.getString(R.string.ws6_got_it_toast)) }.onFailure { nav.toast(it.userMessage() ?: generic) } }
                        }, Modifier.fillMaxWidth(), loading = busy)
                        Text(stringResource(R.string.ws6_got_it_hint), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
                    } else {
                        Row(
                            Modifier.fillMaxWidth().clip(CtShapes.field).background(c.go.copy(alpha = 0.1f)).padding(12.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.go, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (myCall.attendance == com.tff.app.model.Attendance.CONFIRMED) stringResource(R.string.ws6e_got_it_coming, clock(myCall.ackAt))
                                else stringResource(R.string.ws6_got_it_done, clock(myCall.ackAt)),
                                style = Ct.type.bodyS, color = c.go,
                            )
                        }
                    }
                }
            }
            SprocketDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                SectionHeader(stringResource(R.string.ws6_day_where))
                val privateHome = project.shootLocationType == com.tff.app.model.LocationType.PRIVATE_HOME
                if (privateHome || project.bringFriendWelcome) {
                    com.tff.app.ui.components.ChipFlow(Modifier.padding(bottom = 10.dp)) {
                        if (privateHome) CtChip(stringResource(R.string.ws6e_day_private_home), tone = ChipTone.WARN, small = true)
                        if (project.bringFriendWelcome) CtChip(stringResource(R.string.ws6e_day_bring_friend), tone = ChipTone.GO, small = true)
                    }
                }
                Text(day.address, style = Ct.type.body, color = c.text)
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.ws6_open_maps), { nav.openUrl(day.mapsUrl.ifBlank { ShareText.mapsUrl(day.address) }) }, icon = Icons.Rounded.Map)
                Spacer(Modifier.height(8.dp))
                day.parkingNote?.takeIf { it.isNotBlank() }?.let { StackedRow(Icons.Rounded.LocalParking, stringResource(R.string.ws6_day_parking), it) }
                day.hospitalNote?.takeIf { it.isNotBlank() }?.let { StackedRow(Icons.Rounded.LocalHospital, stringResource(R.string.ws6_day_hospital), it) }
                project.safetyMeasures?.takeIf { it.isNotBlank() }?.let { StackedRow(Icons.Rounded.Shield, stringResource(R.string.ws6e_day_safety), it) }
                Spacer(Modifier.height(12.dp))
                SectionHeader(stringResource(R.string.ws6_day_the_day))
                // Wrap-by is already in the call header; the day section keeps the longer notes.
                day.mealsPlan?.takeIf { it.isNotBlank() }?.let { StackedRow(Icons.Rounded.Restaurant, stringResource(R.string.ws6_terms_meals), it) }
                day.notes?.takeIf { it.isNotBlank() }?.let { StackedRow(Icons.AutoMirrored.Rounded.StickyNote2, stringResource(R.string.ws6_terms_notes_label), it) }
                if (lateWrap && !cancelled && !passed) {
                    val wrapAt = Dates.time(day.wrapBy)
                    val transport = project.transport?.takeIf { it != com.tff.app.model.Transport.NONE }?.let { tr ->
                        val base = CardText.transportLabel(tr)
                        if (tr == com.tff.app.model.Transport.MILEAGE_OR_TA_PAID) project.travelAllowancePerDay?.takeIf { it > 0 }
                            ?.let { "$base · ${com.tff.app.core.Money.format(it, pf.roles.firstOrNull()?.currency ?: "INR")}/day" } ?: base
                        else base
                    }
                    val note = stringResource(if (isOwner) R.string.ws6e_day_late_wrap_owner else R.string.ws6e_day_late_wrap, wrapAt)
                    StackedRow(Icons.Rounded.DirectionsCar, stringResource(R.string.ws6e_day_getting_home), listOfNotNull(transport, note).joinToString("\n"))
                    val match = d.myMatch
                    if (!isOwner && match != null) {
                        val prefill = stringResource(R.string.ws6e_day_ask_home_prefill, wrapAt)
                        GhostButton(stringResource(R.string.ws6e_day_ask_home), {
                            com.tff.app.ui.screens.chat.ChatBridge.setPrefill(match.match.id, prefill)
                            nav.toChat(match.match.id)
                        }, Modifier.offset(x = (-16).dp), color = c.accent)
                    }
                }

                if (full.calls.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val acked = full.calls.count { it.call.ackAt != null }
                    SectionHeader(stringResource(R.string.ws6_day_team), count = full.calls.size + 1)
                    if (isOwner) Text(stringResource(R.string.ws6_day_acks, acked, full.calls.size), style = Ct.type.monoS, color = c.textSecondary)
                    // The person running the set is always on the sheet, so nobody walks into a flat not knowing who's in charge.
                    TeamRow(
                        name = pf.owner.shortName, role = stringResource(R.string.ws6e_day_production), call = null,
                        acked = false, showAck = false, me = isOwner, onNudge = null,
                    )
                    full.calls.sortedBy { it.call.callTime }.forEach { cv ->
                        TeamRow(
                            name = cv.shortName, role = CardText.roleName(cv.roleCode), call = Dates.time(cv.call.callTime),
                            acked = cv.call.ackAt != null, showAck = isOwner, me = cv.call.profileId == d.myId,
                            onNudge = if (isOwner && cv.call.ackAt == null && !passed) ({
                                vm.nudge(cv.call.profileId) { r ->
                                    r.onSuccess { nav.toast(res.getString(R.string.ws6_nudged, cv.shortName.removeSuffix("."))) }.onFailure { nav.toast(it.userMessage() ?: generic) }
                                }
                            }) else null,
                        )
                    }
                    Text(stringResource(R.string.ws6_day_no_numbers), style = Ct.type.label, color = c.textMuted, modifier = Modifier.padding(top = 6.dp))
                }

                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isOwner) {
                        PrimaryButton(stringResource(R.string.ws6_share_whatsapp), {
                            nav.share(ShareText.daySheet(project, day, full.dayIndex, full.dayCount, AppContainer.shareBaseUrl), res.getString(R.string.ws6_share_whatsapp))
                        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
                    }
                    SecondaryButton(stringResource(R.string.ws6_add_calendar), {
                        addToCalendar(context, project, day, callTime) { nav.toast(res.getString(R.string.ws6_no_calendar)) }
                    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Event)
                    SecondaryButton(stringResource(R.string.ws6_share_trusted), {
                        nav.share(ShareText.trustedContact(project, day, myCall?.callTime), res.getString(R.string.ws6_share_trusted))
                    }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Shield)
                    if (!isOwner && d.myMatch != null && d.myMatch.match.state == MatchState.BOOKED && !passed && !stillComing && vm.features.withdrawRelease) {
                        GhostButton(stringResource(R.string.ws6_cant_make_it), { withdraw = true }, Modifier.fillMaxWidth(), color = c.stop)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (withdraw && d.myMatch != null) {
        WithdrawSheet(d.myMatch, nav, onDismiss = { withdraw = false }, onDone = { withdraw = false; nav.back() }, preselect = WithdrawReason.DATES_CHANGED)
    }
}

private fun addToCalendar(context: Context, project: Project, day: ShootDay, call: LocalTime, onMissing: () -> Unit) {
    val zone = AppClock.zone
    val begin = day.day.atTime(call).atZone(zone).toInstant().toEpochMilli()
    val endDate = if (day.wrapBy.isAfter(call)) day.day else day.day.plusDays(1)
    val end = endDate.atTime(day.wrapBy).atZone(zone).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.Events.TITLE, "Call ${Dates.time(call)} · ${project.title}")
        .putExtra(CalendarContract.Events.EVENT_LOCATION, day.address.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "))
        .putExtra(CalendarContract.Events.DESCRIPTION, ShareText.calendarDescription(project, day))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { onMissing() }
}

/** Label (icon + label) above a left-aligned value: long day-sheet values stay readable on set. */
@Composable
private fun StackedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val c = Ct.colors
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = Ct.type.label, color = c.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = Ct.type.body, color = c.text, modifier = Modifier.padding(start = 26.dp))
    }
    androidx.compose.material3.HorizontalDivider(color = c.outline.copy(alpha = 0.6f), thickness = 1.dp)
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, danger: Boolean) {
    val c = Ct.colors
    Row(
        Modifier.fillMaxWidth().clip(CtShapes.field).background(if (danger) c.dangerContainer else c.surface)
            .border(1.dp, if (danger) c.stop.copy(alpha = 0.45f) else c.outline, CtShapes.field).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) c.stop else c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Ct.type.bodyS, color = if (danger) c.onDangerContainer else c.textSecondary)
    }
}

/** T-48h "Still coming?" (F11): Yes confirms; Can't make it opens the withdraw flow. */
@Composable
private fun StillComingCard(call: String, busy: Boolean, alsoAcks: Boolean, onYes: () -> Unit, onCant: () -> Unit) {
    val c = Ct.colors
    Column(
        Modifier.fillMaxWidth().clip(CtShapes.card).background(c.warnContainer)
            .border(1.dp, c.onWarnContainer.copy(alpha = 0.4f), CtShapes.card).padding(16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null, tint = c.onWarnContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ws6_still_title), style = Ct.type.titleM, color = c.text)
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.ws6_still_body, call), style = Ct.type.bodyS, color = c.onWarnContainer)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(stringResource(R.string.ws6_still_yes), onYes, Modifier.fillMaxWidth(), loading = busy)
        if (alsoAcks) {
            Text(stringResource(R.string.ws6e_still_yes_hint), style = Ct.type.label, color = c.onWarnContainer.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        SecondaryButton(stringResource(R.string.ws6_cant_make_it), onCant, Modifier.fillMaxWidth(), enabled = !busy)
    }
}

@Composable
private fun TeamRow(name: String, role: String, call: String?, acked: Boolean, showAck: Boolean, me: Boolean, onNudge: (() -> Unit)?) {
    val c = Ct.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Monogram(name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }, size = 36.dp, highlight = me)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (me) stringResource(R.string.ws6_day_you, name) else name, style = Ct.type.bodyStrong.copy(fontSize = Ct.type.bodyS.fontSize), color = c.text)
            Text(role, style = Ct.type.label, color = c.textSecondary)
        }
        if (call != null) TimecodeText(call, style = Ct.type.mono, color = c.text)
        if (showAck) {
            Spacer(Modifier.width(10.dp))
            if (acked) CtChip(stringResource(R.string.ws6_day_acked), tone = ChipTone.GO, small = true)
            else if (onNudge != null) GhostButton(stringResource(R.string.ws6_nudge), onNudge)
            else CtChip(stringResource(R.string.ws6_day_not_acked), small = true)
        }
    }
}
