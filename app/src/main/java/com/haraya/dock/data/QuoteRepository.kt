package com.haraya.dock.data

data class IslamicQuote(
    val arabic: String,
    val translation: String,
    val reference: String
)

object QuoteRepository {
    private val quotes = listOf(
        IslamicQuote(
            arabic = "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ",
            translation = "Glory be to Him Who has subjected this to us, and we could never have it by our efforts.",
            reference = "Surah Az-Zukhruf: 13 (Travel Prayer)"
        ),
        IslamicQuote(
            arabic = "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا • إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            translation = "For indeed, with hardship comes ease. Indeed, with hardship comes ease.",
            reference = "Surah Ash-Sharh: 5-6"
        ),
        IslamicQuote(
            arabic = "وَمَن يَتَّقِ اللَّهَ يَجْعَل لَّهُ مَخْرَجًا وَيَرْزُقْهُ مِنْ حَيْثُ لَا يَحْتَسِبُ",
            translation = "Whoever fears Allah, He will make for him a way out and provide for him from where he does not expect.",
            reference = "Surah At-Talaq: 2-3"
        ),
        IslamicQuote(
            arabic = "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            translation = "Unquestionably, by the remembrance of Allah hearts find peace.",
            reference = "Surah Ar-Ra'd: 28"
        ),
        IslamicQuote(
            arabic = "بِسْمِ اللَّهِ مَجْرَاهَا وَمُرْسَاهَا إِنَّ رَبِّي لَغَفُورٌ رَّحِيمٌ",
            translation = "In the name of Allah is its course and its anchorage. Indeed, my Lord is Forgiving and Merciful.",
            reference = "Surah Hud: 41"
        )
    )

    fun getQuote(index: Int): IslamicQuote {
        val safeIdx = ((index % quotes.size) + quotes.size) % quotes.size
        return quotes[safeIdx]
    }

    fun count(): Int = quotes.size
}
