package net.harutiro.gitappinstaller.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.harutiro.gitappinstaller.domain.TrackedRepo
import java.io.File

class TrackedRepoStore(context: Context) {
    private val file: File = File(context.filesDir, FILE_NAME)
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<TrackedRepo>>(emptyList())
    val items: StateFlow<List<TrackedRepo>> = _items.asStateFlow()

    init {
        scope.launch { reload() }
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = if (file.exists() && file.length() > 0) {
                runCatching {
                    json.decodeFromString(ListSerializer(TrackedRepo.serializer()), file.readText())
                }.getOrDefault(emptyList())
            } else emptyList()
            _items.value = list
        }
    }

    suspend fun add(repo: TrackedRepo) = mutate { current ->
        val nextId = if (repo.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else repo.id
        val withoutDup = current.filterNot { it.host == repo.host && it.owner.equals(repo.owner, true) && it.repo.equals(repo.repo, true) }
        withoutDup + repo.copy(id = nextId)
    }

    suspend fun update(repo: TrackedRepo) = mutate { current ->
        current.map { if (it.id == repo.id) repo else it }
    }

    suspend fun remove(id: Long) = mutate { current ->
        current.filterNot { it.id == id }
    }

    private suspend fun mutate(transform: (List<TrackedRepo>) -> List<TrackedRepo>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_items.value)
            file.writeText(json.encodeToString(ListSerializer(TrackedRepo.serializer()), next))
            _items.value = next
        }
    }

    companion object {
        private const val FILE_NAME = "tracked_repos.json"
    }
}
