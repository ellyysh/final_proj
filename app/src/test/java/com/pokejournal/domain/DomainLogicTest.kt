package com.pokejournal.domain

import com.pokejournal.data.remote.DtoMappers
import com.pokejournal.domain.logic.CachePolicy
import com.pokejournal.domain.logic.NameError
import com.pokejournal.domain.logic.NameValidation
import com.pokejournal.domain.logic.NameValidator
import com.pokejournal.domain.logic.PokemonListBuilder
import com.pokejournal.domain.logic.RelativeTime
import com.pokejournal.domain.logic.StatsCalculator
import com.pokejournal.domain.logic.toUserMessage
import com.pokejournal.domain.model.CatchStatus
import com.pokejournal.domain.model.ListFilter
import com.pokejournal.domain.model.PokemonUserState
import com.pokejournal.domain.model.SortOrder
import com.pokejournal.domain.model.StatusFilter
import com.pokejournal.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CachePolicyTest {
    private val hour = 3_600_000L

    @Test
    fun `missing timestamp is always stale`() {
        assertTrue(CachePolicy.isStale(updatedAt = null, now = 0, ttlMillis = hour))
    }

    @Test
    fun `fresh cache inside ttl is not stale, boundary is stale`() {
        assertFalse(CachePolicy.isStale(updatedAt = 1_000, now = 1_000 + hour - 1, ttlMillis = hour))
        assertTrue(CachePolicy.isStale(updatedAt = 1_000, now = 1_000 + hour, ttlMillis = hour))
    }

    @Test
    fun `clock moved backwards does not force refresh`() {
        assertFalse(CachePolicy.isStale(updatedAt = 5_000, now = 1_000, ttlMillis = hour))
    }

    @Test
    fun `catalog needs refresh when user extends pokedex even if fresh`() {
        assertTrue(CachePolicy.needsCatalogRefresh(1_000, cachedLimit = 151, requiredLimit = 251, now = 1_001, ttlMillis = hour))
        assertFalse(CachePolicy.needsCatalogRefresh(1_000, cachedLimit = 251, requiredLimit = 151, now = 1_001, ttlMillis = hour))
    }
}

class PokemonListBuilderTest {
    private val catalog = TestData.catalog + TestData.chikorita

    private fun build(
        query: String = "",
        filter: ListFilter = ListFilter(),
        states: Map<Int, PokemonUserState> = emptyMap(),
        sort: SortOrder = SortOrder.NUMBER,
        size: Int = 151,
    ) = PokemonListBuilder.build(catalog, states, query, filter, sort, size).map { it.summary.id }

    @Test
    fun `pokedex size limits catalog`() {
        assertEquals(listOf(1, 4, 7, 25, 151), build(size = 151))
        assertEquals(listOf(1, 4, 7, 25, 151, 152), build(size = 251))
    }

    @Test
    fun `search matches name substring or exact number with hash`() {
        assertEquals(listOf(4), build(query = "  CHAR "))
        assertEquals(listOf(25), build(query = "#25"))
        assertEquals(listOf(25), build(query = "25"))
        assertEquals(emptyList<Int>(), build(query = "zzz"))
    }

    @Test
    fun `type and status filters are combined`() {
        val states = mapOf(
            1 to PokemonUserState(1, status = CatchStatus.CAUGHT),
            4 to PokemonUserState(4, status = CatchStatus.CAUGHT),
        )
        assertEquals(listOf(1), build(filter = ListFilter(type = "grass", status = StatusFilter.CAUGHT), states = states))
        assertEquals(listOf(7, 25, 151), build(filter = ListFilter(status = StatusFilter.UNTRACKED), states = states))
    }

    @Test
    fun `hidden pokemon disappear everywhere except hidden filter`() {
        val states = mapOf(7 to PokemonUserState(7, hidden = true, status = CatchStatus.CAUGHT))
        assertFalse(7 in build(states = states))
        assertFalse(7 in build(filter = ListFilter(status = StatusFilter.CAUGHT), states = states))
        assertEquals(listOf(7), build(filter = ListFilter(status = StatusFilter.HIDDEN), states = states))
    }

    @Test
    fun `pinned go first and then chosen sort applies`() {
        val states = mapOf(
            151 to PokemonUserState(151, pinned = true),
            25 to PokemonUserState(25, status = CatchStatus.CAUGHT),
            7 to PokemonUserState(7, status = CatchStatus.WANTED),
        )
        assertEquals(listOf(151, 1, 4, 7, 25), build(states = states, sort = SortOrder.NUMBER))
        assertEquals(listOf(151, 1, 4, 25, 7), build(states = states, sort = SortOrder.NAME))
        assertEquals(listOf(151, 25, 7, 1, 4), build(states = states, sort = SortOrder.STATUS))
    }
}

class NameValidatorTest {
    @Test
    fun `trims and collapses whitespace`() {
        assertEquals(NameValidation.Valid("Dream team"), NameValidator.validate("  Dream   team ", emptyList()))
    }

    @Test
    fun `rejects empty, too long and case-insensitive duplicates`() {
        assertEquals(NameValidation.Invalid(NameError.EMPTY), NameValidator.validate("   ", emptyList()))
        assertEquals(NameValidation.Invalid(NameError.TOO_LONG), NameValidator.validate("x".repeat(41), emptyList()))
        assertEquals(NameValidation.Invalid(NameError.DUPLICATE), NameValidator.validate("dream TEAM", listOf("Dream team")))
    }
}

class StatsCalculatorTest {
    @Test
    fun `computes completion and per type progress within pokedex size`() {
        val catalog = TestData.catalog + TestData.chikorita
        val states = mapOf(
            1 to PokemonUserState(1, status = CatchStatus.CAUGHT),
            152 to PokemonUserState(152, status = CatchStatus.CAUGHT), // вне размера 151
            4 to PokemonUserState(4, status = CatchStatus.WANTED, pinned = true),
        )
        val stats = StatsCalculator.calculate(catalog, states, pokedexSize = 151)

        assertEquals(5, stats.total)
        assertEquals(1, stats.caught)
        assertEquals(1, stats.wanted)
        assertEquals(1, stats.pinned)
        assertEquals(20, stats.completionPercent)
        val grass = stats.byType.first { it.type == "grass" }
        assertEquals(1, grass.caught)
        assertEquals(1, grass.total)
    }

    @Test
    fun `empty catalog gives zero percent without division error`() {
        assertEquals(0, StatsCalculator.calculate(emptyList(), emptyMap(), 151).completionPercent)
    }
}

class MappersTest {
    @Test
    fun `id is parsed from resource url`() {
        assertEquals(25, DtoMappers.idFromUrl("https://pokeapi.co/api/v2/pokemon/25/"))
        assertEquals(null, DtoMappers.idFromUrl("https://pokeapi.co/api/v2/pokemon/"))
    }

    @Test
    fun `flavor text is cleaned from control characters`() {
        assertEquals("A strange seed was planted on its back.", DtoMappers.cleanFlavorText("A strange seed was\nplanted\u000con its back."))
    }

    @Test
    fun `errors are mapped to user friendly messages`() {
        assertEquals("Нет подключения к сети", IOException("x").toUserMessage())
    }

    @Test
    fun `relative time formatting`() {
        assertEquals("никогда", RelativeTime.format(null, 0))
        assertEquals("5 мин назад", RelativeTime.format(0, 5 * 60_000L))
        assertEquals("2 ч назад", RelativeTime.format(0, 2 * 3_600_000L))
    }
}
