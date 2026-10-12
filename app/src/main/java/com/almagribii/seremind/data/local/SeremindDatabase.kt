package com.almagribii.seremind.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.almagribii.seremind.data.local.dao.AppTargetDao
import com.almagribii.seremind.data.local.dao.ArticleDao
import com.almagribii.seremind.data.local.dao.GateEventDao
import com.almagribii.seremind.data.local.dao.JournalEntryDao
import com.almagribii.seremind.data.local.dao.RecoveryEventDao
import com.almagribii.seremind.data.local.dao.UserPreferenceDao
import com.almagribii.seremind.data.local.entity.AppTarget
import com.almagribii.seremind.data.local.entity.Article
import com.almagribii.seremind.data.local.entity.GateEvent
import com.almagribii.seremind.data.local.entity.JournalEntry
import com.almagribii.seremind.data.local.entity.RecoveryEvent
import com.almagribii.seremind.data.local.entity.UserPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AppTarget::class,
        GateEvent::class,
        RecoveryEvent::class,
        JournalEntry::class,
        Article::class,
        UserPreference::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SeremindDatabase : RoomDatabase() {

    abstract fun appTargetDao(): AppTargetDao
    abstract fun gateEventDao(): GateEventDao
    abstract fun recoveryEventDao(): RecoveryEventDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun articleDao(): ArticleDao
    abstract fun userPreferenceDao(): UserPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: SeremindDatabase? = null

        fun getDatabase(context: Context): SeremindDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SeremindDatabase::class.java,
                    "seremind_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        prepopulateData(database)
                    }
                }
            }
        }

        suspend fun prepopulateData(database: SeremindDatabase) {
            val articleDao = database.articleDao()
            val appTargetDao = database.appTargetDao()

            // Prepopulate 8 Educational Articles
            val articles = listOf(
                Article(
                    title = "Apa itu Dopamin",
                    content = "Dopamin sering disalahartikan sebagai molekul kesenangan (pleasure molecule), padahal sebenarnya dopamin adalah molekul motivasi dan antisipasi. Dopamin dilepaskan oleh otak bukan saat kita menikmati sesuatu, melainkan saat kita mengantisipasi atau mencari penghargaan. Inilah mengapa notifikasi HP, feed yang tidak berujung (infinite scroll), dan video pendek sangat adiktif — otak kita terus 'mencari' kejutan berikutnya.",
                    isRead = false
                ),
                Article(
                    title = "Dopamin dan Kebiasaan Otomatis",
                    content = "Ketika suatu tindakan berulang kali memberikan kepuasan instan, jalur saraf di otak menguat dan menjadi otomatis (habit loop). Saat bosan atau cemas, otak secara otomatis memicu dorongan untuk membuka aplikasi tertentu tanpa melibatkan kesadaran rasional. Ini disebut kebiasaan otomatis, di mana cortex prefrontal (pusat keputusan sadar) 'tertidur' dan basal ganglia mengambil alih.",
                    isRead = false
                ),
                Article(
                    title = "Mengapa Kamu Scroll Tanpa Sadar",
                    content = "Pernahkah kamu membuka HP untuk melihat jam, lalu 30 menit kemudian kamu tersadar sedang menelusuri reels atau video tanpa tujuan? Itu terjadi karena mode 'autopilot'. Aplikasi dirancang dengan desain intermiten (variable reward) seperti mesin slot kasino. Setiap kali kamu scroll, ada peluang kecil untuk menemukan sesuatu yang menarik, yang menjaga level dopamin tetap tinggi.",
                    isRead = false
                ),
                Article(
                    title = "Apa itu Action Prediction Error",
                    content = "Riset neurosains terbaru dari UCL menunjukkan bahwa otak memiliki dua sistem pembelajaran dopamin: Reward Prediction Error (RPE) dan Action Prediction Error (APE). Kebiasaan doomscrolling dikendalikan oleh APE, yaitu sistem yang mengupdate seberapa sering suatu aksi dilakukan terlepas dari nilai reward-nya. Kita membuka aplikasi bukan lagi karena mengharapkan sesuatu yang hebat, melainkan karena aksi itu sudah menjadi default policy di otak.",
                    isRead = false
                ),
                Article(
                    title = "Mengapa Memblokir Tidak Cukup",
                    content = "Aplikasi pemblokir konvensional mencoba melawan kebiasaan dengan hukuman atau larangan keras. Namun, otak sering kali mencari celah untuk mematikan blokir tersebut atau memicu rasa frustrasi yang berujung pada pelampiasan yang lebih parah. Pendekatan terbaik bukanlah memblokir, melainkan menginterupsi. Dengan memberikan jeda sejenak, kita membangunkan kesadaran sadar untuk memilih kembali.",
                    isRead = false
                ),
                Article(
                    title = "Cara Kerja Seremind",
                    content = "Seremind hadir bukan sebagai polisi digital yang melarangmu, melainkan sebagai ruang tenang (serenity + mind). Saat kamu membuka aplikasi target, Seremind menghadirkan jeda reflektif. Jeda ini memberi waktu bagi cortex prefrontal untuk aktif kembali, menanyakan alasanmu membuka aplikasi, dan mengembalikan kendali penuh ke tanganmu.",
                    isRead = false
                ),
                Article(
                    title = "Jurnal dan Kesadaran Diri",
                    content = "Menulis jurnal ekspresive (expressive writing) terbukti secara ilmiah membantu meredakan kecemasan dan mengurai emosi terpendam. Sering kali, keinginan membuka HP bukan karena butuh informasi, melainkan pelarian dari rasa bosan, sepi, atau stres. Dengan mencatat emosi di balik setiap kebiasaan, kamu belajar mengenali kebutuhan sejatimu.",
                    isRead = false
                ),
                Article(
                    title = "Emosi di Balik Kebiasaan",
                    content = "Setiap kebiasaan digital membawa pesan tentang kondisi emosionalmu. Apakah kamu sedang lelah dan butuh istirahat, atau cemas dan butuh validasi? Dengan mengenali emosi seperti lelah, cemas, bosan, atau kesepian, kamu dapat merespons dengan cara yang lebih welas asih pada diri sendiri, alih-alih menghakimi diri saat gagal.",
                    isRead = false
                )
            )

            for (article in articles) {
                articleDao.insert(article)
            }

            // Prepopulate Default App Targets
            val defaultTargets = listOf(
                AppTarget(packageName = "com.zhiliaoapp.musically", appName = "TikTok", isActive = true, delaySeconds = 5),
                AppTarget(packageName = "com.instagram.android", appName = "Instagram", isActive = true, delaySeconds = 5),
                AppTarget(packageName = "com.google.android.youtube", appName = "YouTube", isActive = true, delaySeconds = 5),
                AppTarget(packageName = "com.twitter.android", appName = "Twitter/X", isActive = true, delaySeconds = 5),
                AppTarget(packageName = "com.facebook.katana", appName = "Facebook", isActive = true, delaySeconds = 5)
            )

            for (target in defaultTargets) {
                appTargetDao.insert(target)
            }
        }
    }
}
