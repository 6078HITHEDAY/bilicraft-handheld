package com.bilicraft.handheld.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ChatRuleRepository(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val _ruleSet = MutableStateFlow(ChatRuleSet())
    val ruleSet: StateFlow<ChatRuleSet> = _ruleSet.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val loaded = runCatching {
            if (file.exists()) json.decodeFromString<ChatRuleSet>(file.readText()) else null
        }.getOrNull()
        val next = (loaded ?: ChatRuleSet()).mergedWithBuiltins()
        _ruleSet.value = next
        if (loaded == null || next != loaded) save(next)
    }

    suspend fun save(next: ChatRuleSet) = withContext(Dispatchers.IO) {
        _ruleSet.value = next
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(next))
    }

    suspend fun upsert(rule: ChatRule) {
        val current = _ruleSet.value
        val rules = if (current.rules.any { it.id == rule.id }) {
            current.rules.map { if (it.id == rule.id) rule else it }
        } else {
            current.rules + rule
        }
        save(current.copy(rules = rules))
    }

    suspend fun delete(id: String) {
        val current = _ruleSet.value
        save(current.copy(rules = current.rules.filterNot { it.id == id }))
    }

    suspend fun setMsgTemplate(template: String) {
        val trimmed = template.ifBlank { DEFAULT_MSG_TEMPLATE }
        save(_ruleSet.value.copy(msgCommandTemplate = trimmed))
    }
}
