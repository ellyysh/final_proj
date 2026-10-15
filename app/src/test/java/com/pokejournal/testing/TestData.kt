package com.pokejournal.testing

import com.pokejournal.domain.logic.TimeProvider
import com.pokejournal.domain.model.PokemonDetail
import com.pokejournal.domain.model.PokemonImages
import com.pokejournal.domain.model.PokemonStat
import com.pokejournal.domain.model.PokemonSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestWatcher
import org.junit.runner.Description

object TestData {
    fun summary(id: Int, name: String, vararg types: String) =
        PokemonSummary(id, name, PokemonImages.artworkUrl(id), types.toList())

    val bulbasaur = summary(1, "bulbasaur", "grass", "poison")
    val charmander = summary(4, "charmander", "fire")
    val squirtle = summary(7, "squirtle", "water")
    val pikachu = summary(25, "pikachu", "electric")
    val mew = summary(151, "mew", "psychic")
    val chikorita = summary(152, "chikorita", "grass")

    val catalog = listOf(bulbasaur, charmander, squirtle, pikachu, mew)

    fun detail(id: Int, fetchedAt: Long? = 1_000L): PokemonDetail {
        val summary = (catalog + chikorita).firstOrNull { it.id == id } ?: summary(id, "pokemon-$id", "normal")
        return PokemonDetail(
            id = summary.id,
            name = summary.name,
            imageUrl = summary.imageUrl,
            types = summary.types,
            height = 7,
            weight = 69,
            baseExperience = 64,
            genus = "Test Pokémon",
            flavorText = "Flavor",
            abilities = listOf("overgrow"),
            stats = listOf(PokemonStat("hp", 45)),
            fetchedAt = fetchedAt,
        )
    }
}

class FakeTime(var current: Long = 1_000_000L) : TimeProvider {
    override fun now(): Long = current
    fun advance(millis: Long) {
        current += millis
    }
}

/** Подменяет Dispatchers.Main тестовым диспетчером (viewModelScope работает на нём). */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
