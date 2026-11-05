package com.example.reconocimiento.domain

import java.time.Instant

enum class AccessRole { ADMINISTRADOR, PERSONA }
enum class AccessMethod { ROSTRO, HUELLA }

data class AccessStatus(val administratorCreated: Boolean = false, val setupAvailable: Boolean = false)
data class PublicZone(val name: String = "Sede principal", val radius: Double = 100.0, val configured: Boolean = false)
data class AccessSession(val dni: String, val role: AccessRole, val expiresAt: Instant)
data class PersonalAccess(val profile: Person, val session: AccessSession, val alreadyMarked: Boolean)
data class MarkingResult(val attendance: Attendance, val personalAccess: PersonalAccess? = null)
