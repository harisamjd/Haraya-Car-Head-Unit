package com.haraya.dock.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

data class IslamicQuote(
    val arabic: String,
    val translation: String,
    val reference: String
)

/**
 * Automatic Daily Verse & Prayer repository powered by AlQuran Cloud API:
 * Endpoint: https://api.alquran.cloud/v1/ayah/{ref}/editions/quran-uthmani,id.indonesian
 * Provides Arabic Uthmani script + Bahasa Indonesia translation, with offline fallback
 * and automatic rotation.
 */
object QuoteRepository {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Built-in offline fallback verses (Arabic + Indonesian translation)
    private val fallbackQuotes = mutableListOf(
        IslamicQuote(
            arabic = "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ وَإِنَّا إِلَىٰ رَبِّنَا لَمُنقَلِبُونَ",
            translation = "Maha Suci Tuhan yang telah menundukkan semua ini bagi kami padahal kami sebelumnya tidak mampu menguasainya, dan sesungguhnya kami akan kembali kepada Tuhan kami.",
            reference = "QS. Az-Zukhruf: 13-14 (Doa Berkendara)"
        ),
        IslamicQuote(
            arabic = "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا • إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            translation = "Maka sesungguhnya bersama kesulitan ada kemudahan. Sesungguhnya bersama kesulitan ada kemudahan.",
            reference = "QS. Al-Insyirah: 5-6"
        ),
        IslamicQuote(
            arabic = "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            translation = "Ingatlah, hanya dengan mengingat Allah hati menjadi tenteram.",
            reference = "QS. Ar-Ra'd: 28"
        ),
        IslamicQuote(
            arabic = "وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا وَيَرْزُقْهُ مِنْ حَيْثُ لَا يَحْتَسِبُ",
            translation = "Barangsiapa bertakwa kepada Allah niscaya Dia akan mengadakan baginya jalan keluar, dan memberinya rezeki dari arah yang tiada disangka-sangkanya.",
            reference = "QS. At-Talaq: 2-3"
        ),
        IslamicQuote(
            arabic = "بِسْمِ اللَّهِ مَجْرَاهَا وَمُرْسَاهَا ۚ إِنَّ رَبِّي لَغَفُورٌ رَّحِيمٌ",
            translation = "Dengan menyebut nama Allah di waktu berlayar dan berlabuhnya. Sesungguhnya Tuhanku benar-benar Maha Pengampun lagi Maha Penyayang.",
            reference = "QS. Hud: 41"
        ),
        IslamicQuote(
            arabic = "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ",
            translation = "Ya Tuhan kami, berilah kami kebaikan di dunia dan kebaikan di akhirat dan peliharalah kami dari siksa neraka.",
            reference = "QS. Al-Baqarah: 201"
        ),
        IslamicQuote(
            arabic = "لَا يُكَلِّفُ اللَّهُ نَفْسًا إِلَّا وُسْعَهَا",
            translation = "Allah tidak membebani seseorang melainkan sesuai dengan kesanggupannya.",
            reference = "QS. Al-Baqarah: 286"
        ),
        IslamicQuote(
            arabic = "وَقُل رَّبِّ أَدْخِلْنِي مُدْخَلَ صِدْقٍ وَأَخْرِجْنِي مُخْرَجَ صِدْقٍ وَاجْعَل لِّي مِن لَّدُنكَ سُلْطَانًا نَّصِيرًا",
            translation = "Ya Tuhanku, masukkanlah aku secara masuk yang benar dan keluarkanlah (pula) aku secara keluar yang benar dan berikanlah kepadaku dari sisi Engkau kekuasaan yang menolong.",
            reference = "QS. Al-Isra': 80"
        ),
        IslamicQuote(
            arabic = "وَقُل رَّبِّ أَنزِلْنِي مُنزَلًا مُّبَارَكًا وَأَنتَ خَيْرُ الْمُنزِلِينَ",
            translation = "Ya Tuhanku, tempatkanlah aku pada tempat yang diberkati, dan Engkau adalah sebaik-baik Yang memberi tempat.",
            reference = "QS. Al-Mu'minun: 29"
        ),
        IslamicQuote(
            arabic = "فَاذْكُرُونِي أَذْكُرْكُمْ وَاشْكُرُوا لِي وَلَا تَكْفُرُونِ",
            translation = "Maka ingatlah kepada-Ku, Aku pun akan ingat kepadamu. Bersyukurlah kepada-Ku, dan janganlah kamu ingkar kepada-Ku.",
            reference = "QS. Al-Baqarah: 152"
        )
    )

    // Curated pool of concise, meaningful verses + prayers from the Quran for AlQuran Cloud API
    private val curatedAyahRefs = listOf(
        "43:13", "43:14", "94:5", "94:6", "13:28", "65:2", "65:3", "11:41",
        "2:152", "2:153", "2:186", "2:201", "2:286", "3:139", "3:159", "3:173",
        "8:46", "9:40", "9:51", "10:62", "14:7", "16:97", "17:24", "17:80",
        "18:10", "20:46", "20:114", "21:87", "23:29", "28:77", "29:69", "31:17",
        "39:53", "40:60", "55:13", "57:4", "93:4", "93:5"
    )

    @Volatile
    private var currentLiveQuote: IslamicQuote = fallbackQuotes[0]

    @Volatile
    private var isFetching: Boolean = false

    @Volatile
    private var lastAutoFetchMs: Long = 0L

    private var curatedCursor: Int = 0

    fun getCurrentQuote(): IslamicQuote = currentLiveQuote

    fun getQuote(index: Int): IslamicQuote {
        val safeIdx = ((index % fallbackQuotes.size) + fallbackQuotes.size) % fallbackQuotes.size
        return fallbackQuotes[safeIdx]
    }

    fun count(): Int = fallbackQuotes.size

    fun loadCachedOrDefault(context: Context): IslamicQuote {
        val prefs = context.getSharedPreferences("haraya_verse_cache", Context.MODE_PRIVATE)
        val ar = prefs.getString("cached_arabic", null)
        val tr = prefs.getString("cached_translation", null)
        val rf = prefs.getString("cached_reference", null)
        if (!ar.isNullOrBlank() && !tr.isNullOrBlank() && !rf.isNullOrBlank()) {
            currentLiveQuote = IslamicQuote(ar, tr, rf)
        }
        return currentLiveQuote
    }

    /**
     * Automatically fetches a new verse every [intervalMinutes] minutes (default 5 minutes).
     */
    fun autoUpdateIfDue(context: Context, intervalMinutes: Int = 5, onUpdated: (IslamicQuote) -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastAutoFetchMs >= intervalMinutes * 60_000L) {
            fetchNextFromAlQuranApiAsync(context, onUpdated)
        }
    }

    /**
     * Rotates immediately to the next fallback/cached verse for instant UI feedback,
     * and fetches a fresh verse from `api.alquran.cloud` in the background.
     */
    fun fetchNextFromAlQuranApiAsync(context: Context, onUpdated: (IslamicQuote) -> Unit) {
        // Immediate rotation so tap feels instant even on slow network
        curatedCursor = (curatedCursor + 1) % fallbackQuotes.size
        currentLiveQuote = fallbackQuotes[curatedCursor]
        onUpdated(currentLiveQuote)

        if (isFetching) return
        isFetching = true

        Thread {
            try {
                // Alternate between curated inspiring verses and random Quranic ayahs (1..6236)
                val useCurated = Random.nextInt(100) < 70
                val ref = if (useCurated) {
                    curatedAyahRefs[Random.nextInt(curatedAyahRefs.size)]
                } else {
                    Random.nextInt(1, 6237).toString()
                }

                val fetched = queryAlQuranCloud(ref)
                if (fetched != null) {
                    currentLiveQuote = fetched
                    lastAutoFetchMs = System.currentTimeMillis()
                    // Also add to in-memory pool if not already present
                    synchronized(fallbackQuotes) {
                        if (fallbackQuotes.none { it.reference == fetched.reference }) {
                            fallbackQuotes.add(0, fetched)
                            if (fallbackQuotes.size > 30) {
                                fallbackQuotes.removeAt(fallbackQuotes.lastIndex)
                            }
                        }
                    }
                    context.getSharedPreferences("haraya_verse_cache", Context.MODE_PRIVATE)
                        .edit()
                        .putString("cached_arabic", fetched.arabic)
                        .putString("cached_translation", fetched.translation)
                        .putString("cached_reference", fetched.reference)
                        .apply()

                    mainHandler.post {
                        onUpdated(fetched)
                    }
                }
            } catch (_: Exception) {
            } finally {
                isFetching = false
            }
        }.start()
    }

    private fun queryAlQuranCloud(ayahRef: String): IslamicQuote? {
        val endpoint = "https://api.alquran.cloud/v1/ayah/$ayahRef/editions/quran-uthmani,id.indonesian"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HarayaDock/1.0")
        }
        return try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("code") != 200) return null

            val dataArr = root.getJSONArray("data")
            if (dataArr.length() < 2) return null

            val arabicObj = dataArr.getJSONObject(0)
            val indoObj = dataArr.getJSONObject(1)

            val arabicText = arabicObj.optString("text", "").trim()
            val indoText = indoObj.optString("text", "").trim()
            val surahObj = arabicObj.optJSONObject("surah")
            val surahName = surahObj?.optString("englishName", "Al-Qur'an") ?: "Al-Qur'an"
            val ayahNum = arabicObj.optInt("numberInSurah", 1)

            if (arabicText.isEmpty() || indoText.isEmpty()) return null

            // If random ayah was very long (> 220 chars), pick a concise curated ayah instead
            if (indoText.length > 220 && !curatedAyahRefs.contains(ayahRef)) {
                val fallbackRef = curatedAyahRefs[Random.nextInt(curatedAyahRefs.size)]
                return queryAlQuranCloud(fallbackRef)
            }

            IslamicQuote(
                arabic = arabicText,
                translation = indoText,
                reference = "QS. $surahName: $ayahNum"
            )
        } finally {
            conn.disconnect()
        }
    }
}
