package com.condominio.facil

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BaseDadosTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: OcorrenciaDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndList() = runBlocking {
        val ocorrencia = RegistroOcorrencia(
            titulo = "Teste",
            descricao = "Descricao Teste",
            localizacao = "Local Teste",
            situacao = EstadoOcorrencia.PENDENTE
        )
        dao.salvar(ocorrencia)
        
        val lista = dao.listarTodas().first()
        assertEquals(1, lista.size)
        assertEquals("Teste", lista[0].titulo)
    }

    @Test
    fun testUpdateStatus() = runBlocking {
        val ocorrencia = RegistroOcorrencia(
            titulo = "Teste Update",
            descricao = "Desc",
            localizacao = "Local",
            situacao = EstadoOcorrencia.PENDENTE
        )
        dao.salvar(ocorrencia)
        
        val salva = dao.listarTodas().first()[0]
        val atualizada = salva.copy(situacao = EstadoOcorrencia.CONCLUIDA, emailGestor = "sindico@test.com", dataAgendamento = 123456789L)
        dao.atualizar(atualizada)
        
        val resultado = dao.listarTodas().first()[0]
        assertEquals(EstadoOcorrencia.CONCLUIDA, resultado.situacao)
        assertEquals("sindico@test.com", resultado.emailGestor)
    }

    @Test
    fun testCount() = runBlocking {
        assertEquals(0, dao.contar())
        dao.salvar(RegistroOcorrencia(titulo = "T1", descricao = "D1", localizacao = "L1", situacao = EstadoOcorrencia.PENDENTE))
        assertEquals(1, dao.contar())
    }
}
