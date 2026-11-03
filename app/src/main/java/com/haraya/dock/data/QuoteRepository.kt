package com.haraya.dock.data

data class DailyQuote(
    val arabic: String,
    val translation: String,
    val reference: String
)

object QuoteRepository {

    private val quotes = listOf(
        DailyQuote(
            arabic = "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَٰذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ",
            translation = "Maha Suci Allah yang telah menundukkan semua ini bagi kami padahal kami sebelumnya tidak mampu menguasainya.",
            reference = "QS. Az-Zukhruf: 13"
        ),
        DailyQuote(
            arabic = "بِسْمِ اللَّهِ تَوَكَّلْتُ عَلَى اللَّهِ لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
            translation = "Dengan nama Allah, aku bertawakal kepada Allah. Tiada daya dan kekuatan kecuali dengan pertolongan Allah.",
            reference = "HR. Abu Dawud & Tirmidzi"
        ),
        DailyQuote(
            arabic = "فَإِنَّ مَعَ الْعُسْرِ يُسْرًا ، إِنَّ مَعَ الْعُسْرِ يُسْرًا",
            translation = "Maka sesungguhnya bersama kesulitan ada kemudahan, sesungguhnya bersama kesulitan ada kemudahan.",
            reference = "QS. Al-Insyirah: 5-6"
        ),
        DailyQuote(
            arabic = "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ",
            translation = "Wahai Tuhan kami, berilah kami kebaikan di dunia dan kebaikan di akhirat dan lindungilah kami dari siksa neraka.",
            reference = "QS. Al-Baqarah: 201"
        ),
        DailyQuote(
            arabic = "أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ",
            translation = "Ingatlah, hanya dengan mengingat Allah hati menjadi tenteram.",
            reference = "QS. Ar-Ra'd: 28"
        )
    )

    fun count(): Int = quotes.size

    fun getQuote(index: Int): DailyQuote {
        val safeIdx = ((index % quotes.size) + quotes.size) % quotes.size
        return quotes[safeIdx]
    }
}
