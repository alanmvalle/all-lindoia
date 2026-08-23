package com.condominio.facil

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class EstadoOcorrencia(val texto: String) {
    PENDENTE("Pendente"),
    CONCLUIDA("Concluída"),
    CANCELADA("Cancelada")
}

@Entity(tableName = "ocorrencias")
data class RegistroOcorrencia(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val numeroRequisicao: String = "",
    val titulo: String,
    val descricao: String,
    val localizacao: String,
    val situacao: EstadoOcorrencia,
    val emailGestor: String = "",
    val motivoCancelamento: String = "",
    val fotoUri: String = "",
    val dataAgendamento: Long? = null,
    val dataAlteracao: Long = System.currentTimeMillis(),
    val usuarioCriacao: String = "",
    val usuarioAlteracao: String = "",
    val historicoComentarios: String = ""
)

@Dao
interface OcorrenciaDao {
    @Query("SELECT * FROM ocorrencias ORDER BY id DESC")
    fun listarTodas(): Flow<List<RegistroOcorrencia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(ocorrencia: RegistroOcorrencia)

    @Update
    suspend fun atualizar(ocorrencia: RegistroOcorrencia)

    @Query("SELECT COUNT(*) FROM ocorrencias")
    suspend fun contar(): Int
}

@Database(entities = [RegistroOcorrencia::class], version = 5)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): OcorrenciaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun obterBanco(contexto: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instancia = Room.databaseBuilder(contexto.applicationContext, AppDatabase::class.java, "condominio_db")
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instancia
                instancia
            }
        }
    }
}
