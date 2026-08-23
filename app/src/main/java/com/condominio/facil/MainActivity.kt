package com.condominio.facil

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

val CorLaranjaItau = Color(0xFFFF6600)
val CorFundoApp = Color(0xFFF5F5F5)

class GestaoSindicoViewModel(contexto: Context) : ViewModel() {
    private val db = AppDatabase.obterBanco(contexto)
    private val dao = db.dao()

    var usuarioAutenticado by mutableStateOf(false)
    var emailPerfil by mutableStateOf("")
    var usuarioLogado by mutableStateOf("visitante")
    var abaAtiva by mutableIntStateOf(0)
    var rotaAtual by mutableStateOf("login")

    val ocorrencias: StateFlow<List<RegistroOcorrencia>> = dao.listarTodas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (dao.contar() == 0) {
                dao.salvar(RegistroOcorrencia(titulo = "Vazamento Teto", descricao = "Goteira no salão", localizacao = "Salão Festas", situacao = EstadoOcorrencia.PENDENTE, usuarioCriacao = "sistema"))
            }
        }
    }

    fun tentarLogin(u: String, s: String): Boolean {
        if (u == "operador1" && s == "admin1234") {
            usuarioAutenticado = true
            usuarioLogado = u
            rotaAtual = "dashboard"
            return true
        }
        return false
    }

    fun eEmailValido(e: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(e).matches()

    fun mudarSituacao(o: RegistroOcorrencia, s: EstadoOcorrencia, motivo: String = "", foto: String = "", dataAgendamento: Long? = null) {
        viewModelScope.launch {
            dao.atualizar(o.copy(
                situacao = s, 
                emailGestor = emailPerfil, 
                motivoCancelamento = motivo, 
                fotoUri = if (foto.isNotEmpty()) foto else o.fotoUri,
                dataAgendamento = dataAgendamento ?: o.dataAgendamento,
                dataAlteracao = System.currentTimeMillis(),
                usuarioAlteracao = usuarioLogado
            ))
        }
    }

    fun criarNova(t: String, d: String, l: String, f: String) {
        viewModelScope.launch {
            val requisicao = gerarNumeroRequisicao()
            dao.salvar(RegistroOcorrencia(
                numeroRequisicao = requisicao,
                titulo = t, 
                descricao = d, 
                localizacao = l, 
                situacao = EstadoOcorrencia.PENDENTE, 
                fotoUri = f,
                usuarioCriacao = usuarioLogado,
                usuarioAlteracao = usuarioLogado,
                dataAlteracao = System.currentTimeMillis()
            ))
        }
    }

    fun adicionarComentario(o: RegistroOcorrencia, texto: String) {
        if (texto.isBlank()) return
        viewModelScope.launch {
            val novoComentario = "[${formatarData(System.currentTimeMillis())} - $usuarioLogado]: $texto"
            val historicoAtualizado = if (o.historicoComentarios.isEmpty()) novoComentario else "${o.historicoComentarios}\n$novoComentario"
            dao.atualizar(o.copy(
                historicoComentarios = historicoAtualizado,
                dataAlteracao = System.currentTimeMillis(),
                usuarioAlteracao = usuarioLogado
            ))
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: GestaoSindicoViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T = GestaoSindicoViewModel(applicationContext) as T
            })
            MaterialTheme(colorScheme = lightColorScheme(primary = CorLaranjaItau, background = CorFundoApp, surface = Color.White, surfaceVariant = Color.White)) {
                NavegacaoApp(vm)
            }
        }
    }
}

@Composable
fun NavegacaoApp(v: GestaoSindicoViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = CorFundoApp) {
        when (v.rotaAtual) {
            "login" -> TelaAutenticacao(v)
            "dashboard" -> TelaDashboard(v)
            "perfil" -> TelaPerfilGestor(v)
        }
    }
}

@Composable
fun TelaAutenticacao(v: GestaoSindicoViewModel) {
    var u by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    var e by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(
            painter = painterResource(id = R.drawable.app_icon_fg),
            contentDescription = "Logo App",
            modifier = Modifier.size(100.dp).padding(bottom = 16.dp).clip(MaterialTheme.shapes.medium)
        )
        Text("CONDOMÍNIO", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = CorLaranjaItau)
        Text("GESTÃO DO SÍNDICO", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(48.dp))
        OutlinedTextField(value = u, onValueChange = { u = it }, label = { Text("Usuário") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = s, onValueChange = { s = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (e) Text("Credenciais inválidas!", color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = { if (!v.tentarLogin(u, s)) e = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("ACESSAR SISTEMA", fontWeight = FontWeight.Bold) }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Edifício All Lindóia", fontSize = 12.sp, color = Color.LightGray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDashboard(v: GestaoSindicoViewModel) {
    val ctx = LocalContext.current
    val listaTotal by v.ocorrencias.collectAsState()
    var msgAlerta by remember { mutableStateOf(false) }
    var oParaCancelar by remember { mutableStateOf<RegistroOcorrencia?>(null) }
    var idParaAgendar by remember { mutableStateOf<Int?>(null) }
    var mostrarNova by remember { mutableStateOf(false) }

    val ocorrenciaParaAgendar = remember(idParaAgendar, listaTotal) {
        listaTotal.find { it.id == idParaAgendar }
    }

    Scaffold(
        containerColor = CorFundoApp,
        topBar = {
            TopAppBar(
                title = { Text("Painel do Síndico", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { v.rotaAtual = "perfil" }) { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = CorLaranjaItau, modifier = Modifier.size(32.dp)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color.Black)
            )
        },
        bottomBar = {
            ScrollableTabRow(selectedTabIndex = v.abaAtiva, containerColor = Color.White, contentColor = CorLaranjaItau, edgePadding = 16.dp) {
                Tab(selected = v.abaAtiva == 0, onClick = { v.abaAtiva = 0 }, text = { Text("Pendentes") })
                Tab(selected = v.abaAtiva == 1, onClick = { v.abaAtiva = 1 }, text = { Text("Agendadas") })
                Tab(selected = v.abaAtiva == 2, onClick = { v.abaAtiva = 2 }, text = { Text("Concluídas") })
                Tab(selected = v.abaAtiva == 3, onClick = { v.abaAtiva = 3 }, text = { Text("Canceladas") })
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                if (v.eEmailValido(v.emailPerfil)) mostrarNova = true else msgAlerta = true 
            }, containerColor = CorLaranjaItau, contentColor = Color.White) { Icon(Icons.Default.Add, "Nova") }
        }
    ) { p ->
        val lista = when (v.abaAtiva) {
            0 -> {
                listaTotal.filter { 
                    it.situacao == EstadoOcorrencia.PENDENTE &&
                    (it.dataAgendamento == null || it.dataAgendamento <= System.currentTimeMillis())
                }
            }
            1 -> listaTotal.filter { 
                it.situacao == EstadoOcorrencia.PENDENTE &&
                it.dataAgendamento != null && it.dataAgendamento > System.currentTimeMillis() 
            }
            2 -> listaTotal.filter { it.situacao == EstadoOcorrencia.CONCLUIDA }
            else -> listaTotal.filter { it.situacao == EstadoOcorrencia.CANCELADA }
        }

        Column(modifier = Modifier.fillMaxSize().padding(p)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(lista, key = { it.id }) { o ->
                    ItemOcorrencia(o, 
                        aoAtender = {
                            if (v.eEmailValido(v.emailPerfil)) {
                                v.mudarSituacao(o, EstadoOcorrencia.CONCLUIDA)
//                                val intent = Intent(Intent.ACTION_SENDTO).apply {
//                                    data = Uri.parse("mailto:")
//                                    putExtra(Intent.EXTRA_EMAIL, arrayOf(v.emailPerfil))
//                                    putExtra(Intent.EXTRA_SUBJECT, "Req #${o.numeroRequisicao} - Concluída: ${o.titulo}")
//                                    val corpoEmail = StringBuilder()
//                                    corpoEmail.append("A requisição #${o.numeroRequisicao} ('${o.titulo}') foi CONCLUÍDA.\n\n")
//                                    if (o.historicoComentarios.isNotEmpty()) {
//                                        corpoEmail.append("HISTÓRICO DE COMENTÁRIOS:\n")
//                                        corpoEmail.append(o.historicoComentarios)
//                                        corpoEmail.append("\n\n")
//                                    }
//                                    corpoEmail.append("Gestor: ${v.emailPerfil}")
//                                    putExtra(Intent.EXTRA_TEXT, corpoEmail.toString())
//                                }
//                                ctx.startActivity(intent)
                            } else { msgAlerta = true }
                        },
                        aoCancelar = { if (v.eEmailValido(v.emailPerfil)) oParaCancelar = o else msgAlerta = true },
                        aoAgendar = { if (v.eEmailValido(v.emailPerfil)) idParaAgendar = o.id else msgAlerta = true },
                        aoComentar = { texto -> v.adicionarComentario(o, texto) }
                    )
                }
            }
        }

        if (ocorrenciaParaAgendar != null) {
            val datePickerState = rememberDatePickerState(
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        return utcTimeMillis >= System.currentTimeMillis() - 86400000 
                    }
                }
            )
            DatePickerDialog(
                onDismissRequest = { idParaAgendar = null },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            v.mudarSituacao(ocorrenciaParaAgendar, EstadoOcorrencia.PENDENTE, dataAgendamento = it)
                        }
                        idParaAgendar = null
                    }) { Text("AGENDAR") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (msgAlerta) {
            AlertDialog(onDismissRequest = { msgAlerta = false }, title = { Text("E-mail Requerido") }, text = { Text("Cadastre um e-mail válido no perfil.") }, confirmButton = { TextButton(onClick = { msgAlerta = false; v.rotaAtual = "perfil" }) { Text("PERFIL", color = CorLaranjaItau) } })
        }

        if (mostrarNova) {
            var t by remember { mutableStateOf("") }
            var d by remember { mutableStateOf("") }
            var l by remember { mutableStateOf("") }
            var f by remember { mutableStateOf("") }
            var tempUri by remember { mutableStateOf<Uri?>(null) }
            var showPhotoOptions by remember { mutableStateOf(false) }

            val galeriaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let { f = it.toString() }
            }

            val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                if (success) { tempUri?.let { f = it.toString() } }
            }

            AlertDialog(
                onDismissRequest = { mostrarNova = false },
                title = { Text("Nova Ocorrência") },
                text = {
                    Column {
                        OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = l, onValueChange = { l = it }, label = { Text("Local") }, modifier = Modifier.fillMaxWidth())
                        
                        Button(onClick = { showPhotoOptions = true }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (f.isEmpty()) "ANEXAR FOTO" else "FOTO ANEXADA")
                        }
                        if (f.isNotEmpty()) {
                            Text("Caminho: ${f.takeLast(30)}...", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                },
                confirmButton = { Button(onClick = { v.criarNova(t, d, l, f); mostrarNova = false }, enabled = t.isNotEmpty()) { Text("CRIAR") } }
            )

            if (showPhotoOptions) {
                AlertDialog(
                    onDismissRequest = { showPhotoOptions = false },
                    title = { Text("Escolher Foto") },
                    text = { Text("Deseja tirar uma foto agora ou buscar na galeria?") },
                    confirmButton = {
                        TextButton(onClick = {
                            val uri = criarUriTemporaria(ctx)
                            tempUri = uri
                            cameraLauncher.launch(uri)
                            showPhotoOptions = false
                        }) { Text("CÂMERA") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            galeriaLauncher.launch("image/*")
                            showPhotoOptions = false
                        }) { Text("GALERIA") }
                    }
                )
            }
        }

        oParaCancelar?.let { ocorrencia ->
            var m by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { oParaCancelar = null },
                title = { Text("Cancelar") },
                text = { OutlinedTextField(value = m, onValueChange = { m = it }, label = { Text("Motivo") }) },
                confirmButton = {
                    Button(onClick = {
                        v.mudarSituacao(ocorrencia, EstadoOcorrencia.CANCELADA, m)
//                        val intent = Intent(Intent.ACTION_SENDTO).apply {
//                            data = Uri.parse("mailto:")
//                            putExtra(Intent.EXTRA_EMAIL, arrayOf(v.emailPerfil))
//                            putExtra(Intent.EXTRA_SUBJECT, "Req #${ocorrencia.numeroRequisicao} - Cancelado: ${ocorrencia.titulo}")
//                            putExtra(Intent.EXTRA_TEXT, "A requisição #${ocorrencia.numeroRequisicao} ('${ocorrencia.titulo}') foi cancelada.\nMotivo: $m\nGestor: ${v.emailPerfil}")
//                        }
//                        ctx.startActivity(intent)
                        oParaCancelar = null
                    }, enabled = m.isNotBlank()) { Text("CONFIRMAR") }
                }
            )
        }
    }
}

@Composable
fun ItemOcorrencia(
    o: RegistroOcorrencia, 
    aoAtender: () -> Unit, 
    aoCancelar: () -> Unit, 
    aoAgendar: () -> Unit, 
    aoComentar: (String) -> Unit,
    inicialmenteExpandido: Boolean = false
) {
    var expandido by remember { mutableStateOf(inicialmenteExpandido) }
    var novoComentario by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(o.titulo, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black, modifier = Modifier.weight(1f))
            }
            if (o.numeroRequisicao.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "#${o.numeroRequisicao}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.LightGray
                    )
                }
            }
            Text(o.localizacao, color = CorLaranjaItau, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Datas conforme status
            when {
                o.situacao == EstadoOcorrencia.CANCELADA -> {
                    Text("Cancelado em: ${formatarData(o.dataAlteracao)}", fontSize = 12.sp, color = Color.Red)
                }
                o.situacao == EstadoOcorrencia.CONCLUIDA -> {
                    Text("✅ Concluída em: ${formatarData(o.dataAlteracao)}", fontSize = 12.sp, color = Color(0xFF388E3C))
                }
                o.dataAgendamento != null && o.dataAgendamento > System.currentTimeMillis() -> {
                    Text("📅 Agendado para: ${formatarData(o.dataAgendamento)}", fontSize = 12.sp, color = Color.Blue, fontWeight = FontWeight.Bold)
                }
                else -> {
                    Text("Criado em: ${formatarData(o.dataAlteracao)}", fontSize = 12.sp, color = Color.Gray)
                }
            }

            if (expandido) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DadoTecnico("Descrição:", o.descricao)
                    DadoTecnico("Autor:", o.usuarioCriacao)
                    DadoTecnico("Última Alteração:", formatarData(o.dataAlteracao))
                    if (o.usuarioAlteracao.isNotEmpty()) DadoTecnico("Alterado por:", o.usuarioAlteracao)
                    if (o.emailGestor.isNotEmpty()) DadoTecnico("Email gestão:", o.emailGestor)
                    if (o.motivoCancelamento.isNotEmpty()) DadoTecnico("Motivo Cancelamento:", o.motivoCancelamento)
                    if (o.fotoUri.isNotEmpty()) Text("📷 Foto Anexada", fontSize = 12.sp, color = CorLaranjaItau)
                    
                    if (o.historicoComentarios.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Histórico de Comentários:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(o.historicoComentarios, fontSize = 11.sp, color = Color.DarkGray)
                    }
                }

                if (o.situacao == EstadoOcorrencia.PENDENTE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = novoComentario,
                        onValueChange = { if (it.length <= 250) novoComentario = it },
                        label = { Text("Novo Comentário (máx 250 car.)") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("${novoComentario.length}/250") }
                    )
                    Button(
                        onClick = {
                            aoComentar(novoComentario)
                            novoComentario = ""
                        },
                        enabled = novoComentario.isNotBlank(),
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = CorLaranjaItau)
                    ) {
                        Text("ADICIONAR COMENTÁRIO")
                    }

                    if (o.situacao == EstadoOcorrencia.PENDENTE) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = aoCancelar, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray, contentColor = Color.Black), modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("CANCELAR", fontSize = 9.sp) }
                        OutlinedButton(onClick = aoAgendar, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) { Text("AGENDAR", fontSize = 9.sp) }
                        Button(onClick = aoAtender, colors = ButtonDefaults.buttonColors(containerColor = CorLaranjaItau, contentColor = Color.White), modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("ATENDER", fontSize = 9.sp) }
                        }
                    }
                }
                
                TextButton(onClick = { expandido = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("FECHAR", color = Color.Gray)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                    if (o.situacao == EstadoOcorrencia.PENDENTE) {
                        Button(onClick = aoCancelar, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray, contentColor = Color.Black), modifier = Modifier.padding(end = 8.dp)) { Text("CANCELAR") }
                    }
                    Button(onClick = { expandido = true }, colors = ButtonDefaults.buttonColors(containerColor = CorLaranjaItau, contentColor = Color.White)) { Text("ABRIR") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTelaAutenticacao() {
    MaterialTheme(colorScheme = lightColorScheme(primary = CorLaranjaItau, background = CorFundoApp, surface = Color.White)) {
        TelaAutenticacao(GestaoSindicoViewModel(LocalContext.current))
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewItemOcorrencia() {
    MaterialTheme {
        ItemOcorrencia(
            o = RegistroOcorrencia(
                numeroRequisicao = "20260822150000",
                titulo = "Vazamento no Hall",
                descricao = "Goteira persistente no teto da entrada principal.",
                localizacao = "Entrada Principal",
                situacao = EstadoOcorrencia.PENDENTE,
                usuarioCriacao = "morador_123"
            ),
            aoAtender = {},
            aoCancelar = {},
            aoAgendar = {},
            aoComentar = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewItemOcorrenciaExpandido() {
    MaterialTheme {
        ItemOcorrencia(
            o = RegistroOcorrencia(
                numeroRequisicao = "20260822153000",
                titulo = "Manutenção Elevador",
                descricao = "Elevador social parando entre andares.",
                localizacao = "Bloco B",
                situacao = EstadoOcorrencia.PENDENTE,
                usuarioCriacao = "Joao Silva",
                usuarioAlteracao = "operador1",
                emailGestor = "sindico@condo.com",
                historicoComentarios = "[22/08/2026 - operador1]: Peça encomendada.\n[22/08/2026 - operador1]: Previsão de chegada amanhã."
            ),
            aoAtender = {},
            aoCancelar = {},
            aoAgendar = {},
            aoComentar = {},
            inicialmenteExpandido = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTelaDashboard() {
    val vm = GestaoSindicoViewModel(LocalContext.current)
    vm.emailPerfil = "sindico@alllindoia.com"
    MaterialTheme(colorScheme = lightColorScheme(primary = CorLaranjaItau, background = CorFundoApp, surface = Color.White)) {
        TelaDashboard(vm)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTelaPerfilGestor() {
    val vm = GestaoSindicoViewModel(LocalContext.current)
    vm.emailPerfil = "gestor@condo.com"
    MaterialTheme(colorScheme = lightColorScheme(primary = CorLaranjaItau, background = CorFundoApp, surface = Color.White)) {
        TelaPerfilGestor(vm)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewListaOcorrencias() {
    val sampleList = listOf(
        RegistroOcorrencia(id = 1, numeroRequisicao = "20260822001", titulo = "Luz Queimada", descricao = "Lâmpada do corredor 4º andar", localizacao = "Corredor 4", situacao = EstadoOcorrencia.PENDENTE),
        RegistroOcorrencia(id = 2, numeroRequisicao = "20260822002", titulo = "Pintura Desgastada", descricao = "Parede do elevador riscada", localizacao = "Elevador Social", situacao = EstadoOcorrencia.PENDENTE, dataAgendamento = System.currentTimeMillis() + 86400000, emailGestor = "sindico@test.com"),
        RegistroOcorrencia(id = 3, numeroRequisicao = "20260822003", titulo = "Limpeza Necessária", descricao = "Sujeira no playground", localizacao = "Playground", situacao = EstadoOcorrencia.CONCLUIDA, emailGestor = "sindico@test.com")
    )
    MaterialTheme(colorScheme = lightColorScheme(primary = CorLaranjaItau, background = CorFundoApp, surface = Color.White)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            sampleList.forEach { o ->
                ItemOcorrencia(o, {}, {}, {}, {}, false)
            }
        }
    }
}

@Composable
fun DadoTecnico(label: String, valor: String) {
    Row {
        Text("$label ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
        Text(valor, fontSize = 12.sp, color = Color.Black)
    }
}

@Composable
fun TelaPerfilGestor(v: GestaoSindicoViewModel) {
    var t by remember { mutableStateOf(v.emailPerfil) }
    var s by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { v.rotaAtual = "dashboard" }) { Icon(Icons.Default.ArrowBack, null) }
            Text("Perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(40.dp))
        OutlinedTextField(value = t, onValueChange = { t = it; s = false }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), isError = t.isNotEmpty() && !v.eEmailValido(t))
        Button(onClick = { if (v.eEmailValido(t)) { v.emailPerfil = t; s = true } }, modifier = Modifier.fillMaxWidth().padding(top = 32.dp), enabled = v.eEmailValido(t)) { Text("SALVAR") }
        if (s) Text("Salvo!", color = Color(0xFF388E3C), modifier = Modifier.padding(top = 16.dp))
        Spacer(modifier = Modifier.weight(1f))
        Text("Edifício All Lindóia", fontSize = 10.sp, color = Color.LightGray)
    }
}

private fun formatarData(millis: Long): String {
    val date = java.util.Date(millis)
    val format = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
    return format.format(date)
}

private fun gerarNumeroRequisicao(): String {
    val date = java.util.Date()
    val format = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
    return format.format(date) + System.currentTimeMillis().toString().takeLast(6)
}

private fun criarUriTemporaria(context: Context): Uri {
    val directory = File(context.cacheDir, "images")
    if (!directory.exists()) directory.mkdirs()
    val file = File.createTempFile("camera_image_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "com.condominio.facil.fileprovider", file)
}
