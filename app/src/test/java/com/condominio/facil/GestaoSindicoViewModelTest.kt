package com.condominio.facil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GestaoSindicoViewModelTest {

    private lateinit var viewModel: GestaoSindicoViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        viewModel = GestaoSindicoViewModel(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test login success`() {
        val result = viewModel.tentarLogin("operador1", "admin1234")
        assertTrue(result)
        assertTrue(viewModel.usuarioAutenticado)
        assertEquals("dashboard", viewModel.rotaAtual)
    }

    @Test
    fun `test login failure`() {
        val result = viewModel.tentarLogin("errado", "senha")
        assertFalse(result)
        assertFalse(viewModel.usuarioAutenticado)
        assertEquals("login", viewModel.rotaAtual)
    }

    @Test
    fun `test email validation`() {
        assertTrue(viewModel.eEmailValido("sindico@condo.com"))
        assertFalse(viewModel.eEmailValido("email-invalido"))
    }

    @Test
    fun `test creation and status change`() = runTest {
        viewModel.emailPerfil = "gestor@test.com"
        
        // Create new
        viewModel.criarNova("Vazamento", "Goteira", "Garagem", "foto.jpg")
        
        // Wait for flow to emit
        val ocorrencias = viewModel.ocorrencias.first { it.any { o -> o.titulo == "Vazamento" } }
        val nova = ocorrencias.find { it.titulo == "Vazamento" }
        assertNotNull(nova)
        assertEquals(EstadoOcorrencia.PENDENTE, nova?.situacao)

        // Change status
        viewModel.mudarSituacao(nova!!, EstadoOcorrencia.PENDENTE, dataAgendamento = System.currentTimeMillis() + 86400000)
        
        val ocorrenciasAtualizadas = viewModel.ocorrencias.first { it.any { o -> o.titulo == "Vazamento" && o.dataAgendamento != null } }
        val atualizada = ocorrenciasAtualizadas.find { it.titulo == "Vazamento" }
        assertNotNull(atualizada?.dataAgendamento)
        assertEquals("gestor@test.com", atualizada?.emailGestor)
    }
}
