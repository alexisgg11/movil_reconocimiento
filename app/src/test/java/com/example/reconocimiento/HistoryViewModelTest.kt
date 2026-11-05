package com.example.reconocimiento

import com.example.reconocimiento.domain.*
import com.example.reconocimiento.ui.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Instant

class HistoryViewModelTest {
    private class Repository : AttendanceRepository {
        var profiles=listOf(Person("12345678","Ana Pérez","",true,false))
        val days=mutableMapOf<String,List<Attendance>>()
        val queries=mutableListOf<String?>()
        var offline=false
        var lookup: (suspend (String?)->List<Attendance>)?=null
        override suspend fun people()=profiles
        override suspend fun site()=Site(latitude=0.0,longitude=0.0)
        override suspend fun attendance(date: String?): List<Attendance> {
            queries.add(date);if(offline)throw AppFailure("Sin conexión")
            return lookup?.invoke(date) ?: days[date].orEmpty()
        }
        override suspend fun save(change: PersonChange)=error("Sin escritura en pruebas de historial")
        override suspend fun delete(dni: String){}
        override suspend fun unbind(dni: String){}
        override suspend fun configure(site: Site)=site
        override suspend fun challenge(purpose: String,position: Position?,publicKey: String?,dni: String?)=error("Sin reto")
        override suspend fun bind(challenge: Challenge,signature: String,capture: FaceCapture){}
        override suspend fun mark(challenge: Challenge,signature: String?,capture: FaceCapture?)=error("Sin marcación")
    }
    private fun dayBefore(days: Long)=LocalDate.parse(attendanceToday()).minusDays(days).toString()
    private fun record(date: String,name: String="Ana Pérez")=Attendance(name,"12345678","${date}T12:00:00Z","FACIAL",2.0,.95,id=7)
    private fun signedIn(repo: Repository) = AppViewModel(repo).apply {
        startSession(AccessSession("00000001", AccessRole.ADMINISTRADOR, Instant.MAX))
    }

    @Test fun selectingAPastDayAndPollingKeepTheSameDate()=runBlocking {
        val repo=Repository();val day=dayBefore(1);repo.days[day]=listOf(record(day))
        val vm=signedIn(repo);vm.selectAttendanceDate(day);vm.synchronize()
        assertEquals(listOf(day,day),repo.queries);assertEquals(day,vm.state.value.attendanceDate)
        assertFalse(vm.state.value.followToday);assertEquals(1,vm.state.value.attendance.size)
    }
    @Test fun aLateResponseCannotReplaceTheMostRecentlySelectedDate()=runBlocking {
        val repo=Repository();val earlier=dayBefore(2);val recent=dayBefore(1)
        val waiting=CompletableDeferred<List<Attendance>>()
        repo.lookup={date->if(date==earlier)waiting.await() else listOf(record(recent))}
        val vm=signedIn(repo)
        val first=launch(start=CoroutineStart.UNDISPATCHED){vm.selectAttendanceDate(earlier)}
        vm.selectAttendanceDate(recent);waiting.complete(listOf(record(earlier)));first.join()
        assertEquals(recent,vm.state.value.attendanceDate);assertEquals("${recent}T12:00:00Z",vm.state.value.attendance.single().instant)
    }
    @Test fun pollingUpdatesOpenDetailsAndReflectsDeletedPeopleWithoutDeletingHistory()=runBlocking {
        val repo=Repository();val day=dayBefore(1);repo.days[day]=listOf(record(day))
        val vm=signedIn(repo);vm.selectAttendanceDate(day);vm.synchronize()
        vm.change {it.copy(detail=repo.profiles.single(),attendanceDetail=it.attendance.single())}
        repo.profiles=emptyList();repo.days[day]=listOf(record(day,"Nombre actualizado en la base"))
        vm.synchronize()
        assertTrue(vm.state.value.people.isEmpty());assertNull(vm.state.value.detail)
        assertEquals("Nombre actualizado en la base",vm.state.value.attendanceDetail!!.name)
        assertEquals(1,vm.state.value.attendance.size)
        repo.days[day]=emptyList();vm.synchronize();assertNull(vm.state.value.attendanceDetail)
    }
    @Test fun connectionFailuresKeepExistingDataAndRecoveryClearsTheIndicator()=runBlocking {
        val repo=Repository();val day=dayBefore(1);repo.days[day]=listOf(record(day))
        val vm=signedIn(repo);vm.selectAttendanceDate(day)
        repo.offline=true;vm.synchronize();assertTrue(vm.state.value.syncUnavailable);assertEquals(1,vm.state.value.attendance.size)
        repo.offline=false;vm.synchronize();assertFalse(vm.state.value.syncUnavailable)
    }
    @Test fun failedHistoryQueriesDoNotLookLikeAnEmptySuccessfulDay()=runBlocking {
        val repo=Repository().apply {offline=true};val vm=signedIn(repo)
        try {vm.selectAttendanceDate(dayBefore(1));fail("Debe fallar la consulta")}catch(_: AppFailure){}
        assertNotNull(vm.state.value.attendanceError)
    }
    @Test fun futureDatesAreRejectedAndTodayCanBeRestoredAfterAMark()=runBlocking {
        val repo=Repository();val vm=signedIn(repo)
        try {vm.selectAttendanceDate(LocalDate.parse(attendanceToday()).plusDays(1).toString());fail("No se admiten fechas futuras")}catch(_: AppFailure){}
        assertTrue(repo.queries.isEmpty())
        vm.selectAttendanceDate(dayBefore(2));vm.returnToToday();vm.refresh()
        assertEquals(attendanceToday(),vm.state.value.attendanceDate);assertTrue(vm.state.value.followToday)
        assertEquals(attendanceToday(),repo.queries.last())
    }
    @Test fun gpsUncertaintyAndRealDistanceHaveDifferentShortMessages() {
        val uncertain=ZoneCheck(false,"",1.0,10.0,3.0,true,"FUERA_DE_ZONA")
        val outside=ZoneCheck(false,"",8.0,2.0,3.0,true,"FUERA_DE_ZONA")
        assertTrue(uncertain.explanation().contains("GPS"));assertFalse(uncertain.explanation().contains("Estás fuera"))
        assertTrue(outside.explanation().contains("Estás fuera"))
        assertTrue(ZoneCheck(false,"",8.0,10.0,3.0,true,"FUERA_DE_ZONA").explanation().contains("GPS"))
    }
}
