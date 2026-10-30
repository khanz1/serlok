package com.khanz1.serlok

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import com.khanz1.serlok.data.model.RoomMember
import com.khanz1.serlok.service.RoomAlert
import com.khanz1.serlok.ui.screens.room.IncidentBanner
import com.khanz1.serlok.ui.screens.room.MemberSosSheet
import com.khanz1.serlok.ui.screens.room.ShakeStatusCard
import com.khanz1.serlok.ui.theme.SerlokTheme
import org.junit.Rule
import org.junit.Test

class RoomInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shakeStatus_reflectsArmedAndActiveStates() {
        val isSharing = mutableStateOf(false)
        compose.setContent {
            SerlokTheme {
                ShakeStatusCard(isSharing = isSharing.value, threshold = 3.5f)
            }
        }
        compose.onNodeWithText("Shake detection inactive").assertIsDisplayed()
        compose.onNodeWithText("The 3.5 g detector starts with the Safety Session.")
            .assertIsDisplayed()

        compose.runOnIdle { isSharing.value = true }
        compose.onNodeWithText("Shake detection active").assertIsDisplayed()
        compose.onNodeWithText("Alerts trigger at 3.5 g with a three-second cooldown.").assertIsDisplayed()
        compose.assertNoUnlabeledActions()
    }

    @Test
    fun shakeIncident_displaysMagnitudeWithoutResolutionActions() {
        val alert = mutableStateOf(RoomAlert(
            incidentId = "incident-1",
            userId = "device-1",
            name = "Member E2E",
            magnitudeG = 4.3f,
            latitude = -6.2,
            longitude = 106.8,
            triggeredAt = "2026-08-17T14:00:00.000Z",
        ))
        compose.setContent {
            SerlokTheme {
                IncidentBanner(alert = alert.value, onDismiss = {})
            }
        }
        compose.onNodeWithText("4.3 g shake").assertIsDisplayed()
        compose.onAllNodesWithText("Mark safe").assertCountEquals(0)
        compose.onAllNodesWithText("False alarm").assertCountEquals(0)
        compose.assertNoUnlabeledActions()
    }

    @Test
    fun memberSosSheet_listsThisRoomAlertsWithoutMarkSafe() {
        val member = RoomMember(
            userId = "member",
            name = "Kartika Widyasari",
            isMaster = false,
        )
        val incidents = listOf(
            RoomAlert(
                incidentId = "incident-1",
                userId = "member",
                name = "Kartika Widyasari",
                magnitudeG = 4.3f,
                latitude = -6.2,
                longitude = 106.8,
                triggeredAt = "2026-08-25T07:02:00.000Z",
            ),
            RoomAlert(
                incidentId = "incident-2",
                userId = "member",
                name = "Kartika Widyasari",
                magnitudeG = 3.9f,
                latitude = null,
                longitude = null,
                triggeredAt = "2026-08-25T06:47:00.000Z",
            ),
        )
        compose.setContent {
            SerlokTheme {
                MemberSosSheet(
                    member = member,
                    isSelf = false,
                    incidents = incidents,
                    selectedIncidentId = "incident-1",
                    onBack = {},
                    onIncident = {},
                )
            }
        }
        compose.onNodeWithText("Kartika Widyasari").assertIsDisplayed()
        compose.onNodeWithText("SOS alerts").assertIsDisplayed()
        compose.onNodeWithText("2 in this Room").assertIsDisplayed()
        compose.onNodeWithText("4.3 g shake").assertIsDisplayed()
        compose.onNodeWithText("3.9 g shake").assertIsDisplayed()
        compose.onNodeWithText("Location unavailable").assertIsDisplayed()
        compose.onAllNodesWithText("Mark safe").assertCountEquals(0)
        compose.onAllNodesWithText("False alarm").assertCountEquals(0)
        compose.assertNoUnlabeledActions()
    }

    @Test
    fun memberSosSheet_showsEmptyCopyForThisRoom() {
        val member = RoomMember(
            userId = "host",
            name = "Angga Maulana",
            isMaster = true,
        )
        compose.setContent {
            SerlokTheme {
                MemberSosSheet(
                    member = member,
                    isSelf = true,
                    incidents = emptyList(),
                    selectedIncidentId = null,
                    onBack = {},
                    onIncident = {},
                )
            }
        }
        compose.onNodeWithText("Angga Maulana").assertIsDisplayed()
        compose.onNodeWithText("You").assertIsDisplayed()
        compose.onNodeWithText("0 in this Room").assertIsDisplayed()
        compose.onNodeWithText("No SOS alerts in this Room yet").assertIsDisplayed()
        compose.assertNoUnlabeledActions()
    }
}
