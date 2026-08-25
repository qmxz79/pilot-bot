package com.qmxz.pilotbot.memory

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryStoreTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var memoryStore: MemoryStore

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        memoryStore = MemoryStore(fakePrefs)
    }

    @Test
    fun testUserMemoryJsonRoundTrip() {
        val original = UserMemory(
            userName = "老王",
            homeAddress = "望京南",
            companyAddress = "国贸",
            preferences = listOf("喜欢走高速", "不走拥堵路段"),
            facts = listOf("喜欢喝美式咖啡", "上周去过张家口自驾"),
        )
        val json = original.toJson()
        val parsed = UserMemory.fromJson(json)

        assertNotNull(parsed)
        assertEquals("老王", parsed.userName)
        assertEquals("望京南", parsed.homeAddress)
        assertEquals("国贸", parsed.companyAddress)
        assertEquals(listOf("喜欢走高速", "不走拥堵路段"), parsed.preferences)
        assertEquals(listOf("喜欢喝美式咖啡", "上周去过张家口自驾"), parsed.facts)
    }

    @Test
    fun testUserMemoryFromJsonMalformed() {
        val emptyOnMalformed = UserMemory.fromJson("invalid_json")
        assertEquals("", emptyOnMalformed.userName)
        assertEquals("", emptyOnMalformed.homeAddress)
        assertEquals("", emptyOnMalformed.companyAddress)
        assertTrue(emptyOnMalformed.preferences.isEmpty())
        assertTrue(emptyOnMalformed.facts.isEmpty())

        val emptyOnBlank = UserMemory.fromJson("   ")
        assertEquals("", emptyOnBlank.userName)
    }

    @Test
    fun testInitialGetMemoryIsEmpty() {
        val memory = memoryStore.getMemory()
        assertEquals("", memory.userName)
        assertEquals("", memory.homeAddress)
        assertEquals("", memory.companyAddress)
        assertTrue(memory.preferences.isEmpty())
        assertTrue(memory.facts.isEmpty())
    }

    @Test
    fun testSettersAndUpdateMemory() {
        memoryStore.setUserName("老张")
        memoryStore.setHomeAddress("回龙观东大街")
        memoryStore.setCompanyAddress("中关村软件园")

        val memory = memoryStore.getMemory()
        assertEquals("老张", memory.userName)
        assertEquals("回龙观东大街", memory.homeAddress)
        assertEquals("中关村软件园", memory.companyAddress)
    }

    @Test
    fun testAddAndRemoveFacts() {
        memoryStore.addFact("喜欢吃川菜")
        memoryStore.addFact("每天早上7点半出门")
        memoryStore.addFact("  ") // should be ignored

        var memory = memoryStore.getMemory()
        assertEquals(2, memory.facts.size)
        assertEquals("喜欢吃川菜", memory.facts[0])
        assertEquals("每天早上7点半出门", memory.facts[1])

        // Remove out of bounds (negative / too large) -> no-op
        memoryStore.removeFact(-1)
        memoryStore.removeFact(99)
        assertEquals(2, memoryStore.getMemory().facts.size)

        // Remove first fact
        memoryStore.removeFact(0)
        memory = memoryStore.getMemory()
        assertEquals(1, memory.facts.size)
        assertEquals("每天早上7点半出门", memory.facts[0])

        // Remove remaining fact
        memoryStore.removeFact(0)
        assertTrue(memoryStore.getMemory().facts.isEmpty())
    }

    @Test
    fun testAddAndRemovePreferences() {
        memoryStore.addPreference("走高速优先")
        memoryStore.addPreference("尽量少走收费路段")

        var memory = memoryStore.getMemory()
        assertEquals(2, memory.preferences.size)
        assertEquals("走高速优先", memory.preferences[0])

        memoryStore.removePreference(0)
        memory = memoryStore.getMemory()
        assertEquals(1, memory.preferences.size)
        assertEquals("尽量少走收费路段", memory.preferences[0])
    }

    @Test
    fun testBuildMemoryPromptEmpty() {
        val prompt = memoryStore.buildMemoryPrompt()
        assertEquals("", prompt)
    }

    @Test
    fun testBuildMemoryPromptWithData() {
        memoryStore.setUserName("老王")
        memoryStore.setHomeAddress("望京南")
        memoryStore.setCompanyAddress("国贸")
        memoryStore.addPreference("喜欢走高速")
        memoryStore.addFact("喜欢喝美式咖啡")
        memoryStore.addFact("上周去过张家口自驾")

        val prompt = memoryStore.buildMemoryPrompt()
        assertTrue(prompt.contains("【老朋友的记忆】"))
        assertTrue(prompt.contains("车主称呼：老王"))
        assertTrue(prompt.contains("家地址：望京南"))
        assertTrue(prompt.contains("公司地址：国贸"))
        assertTrue(prompt.contains("驾驶偏好：喜欢走高速"))
        assertTrue(prompt.contains("关于车主的点滴记忆："))
        assertTrue(prompt.contains("* 喜欢喝美式咖啡"))
        assertTrue(prompt.contains("* 上周去过张家口自驾"))
        assertTrue(prompt.contains("像认识很久的老朋友一样"))
    }

    @Test
    fun testSaveAndRetrieveWholeMemory() {
        val memory = UserMemory(
            userName = "小李",
            homeAddress = "通州北苑",
            companyAddress = "望京SOHO",
            preferences = listOf("喜欢听爵士乐"),
            facts = listOf("家里养了一只柴犬"),
        )
        memoryStore.saveMemory(memory)

        val retrieved = memoryStore.getMemory()
        assertEquals(memory, retrieved)
    }

    /**
     * In-memory implementation of [SharedPreferences] for unit testing without Android runtime.
     */
    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data

        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        private inner class FakeEditor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                key?.let { temp[it] = values }
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { removed.add(it) }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) {
                    data.clear()
                }
                removed.forEach { data.remove(it) }
                temp.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
