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
    version = 6,
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
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(true)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }

        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN ai_feedback TEXT")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN ai_feedback_error TEXT")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_targets ADD COLUMN max_session_minutes INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE app_targets ADD COLUMN cooldown_minutes INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE app_targets ADD COLUMN last_session_end_time INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE app_targets_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            package_name TEXT NOT NULL,
                            app_name TEXT NOT NULL,
                            is_active INTEGER NOT NULL,
                            delay_seconds INTEGER NOT NULL,
                            max_session_minutes INTEGER NOT NULL DEFAULT 0,
                            cooldown_minutes INTEGER NOT NULL DEFAULT 0,
                            last_session_end_time INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO app_targets_new
                        SELECT id, package_name, app_name, is_active, delay_seconds,
                            max_session_minutes, cooldown_minutes, last_session_end_time
                        FROM app_targets
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE app_targets")
                    db.execSQL("ALTER TABLE app_targets_new RENAME TO app_targets")
                }
            }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN image_res_name TEXT")
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
            prepopulateArticles(articleDao)
        }

        suspend fun prepopulateArticles(articleDao: com.almagribii.seremind.data.local.dao.ArticleDao) {
            articleDao.deleteAll()
            val articles = listOf(
                Article(
                    title = "Apa itu Dopamin?",
                    content = """
                        Dopamin sering disalahartikan sebagai molekul kesenangan ("pleasure molecule"), padahal riset neurosains modern menunjukkan bahwa dopamin sebenarnya adalah molekul motivasi, dorongan, dan antisipasi. Dopamin dilepaskan oleh sistem imbalan di otak kita bukan saat kita sedang menikmati sesuatu, melainkan pada momen antisipasi sebelum imbalan itu diperoleh.

                        Setiap kali kamu melihat HP berdering, menerima notifikasi, atau menggeser layar (scrolling), otakmu melepas dopamin kecil yang memunculkan rasa penasaran dan dorongan kuat untuk segera bertindak. Sensasi "siapa tahu ada notifikasi menarik" inilah yang menjaga perhatianmu terus terpikat pada layar.

                        Sistem dopaminergi ini sejatinya berevolusi untuk membantu manusia purba berburu dan mencari makanan demi bertahan hidup. Namun di era digital saat ini, algoritma media sosial memanfaatkan mekanisme biologis yang sama dengan menyediakan imbalan tak terduga (variable rewards) yang tiada habisnya.

                        Penting untuk dipahami bahwa dopamin tidak membuatmu merasa bahagia atau puas secara permanen. Sebaliknya, penurunan kadar dopamin setelah lonjakan singkat justru sering kali meninggalkan perasaan hampa, gelisah, dan dorongan untuk kembali mencari stimulasi baru berikutnya.

                        Dengan menyadari cara kerja dopamin dalam pikiranmu, kamu tidak perlu menghukum atau membenci dirimu saat muncul keinginan membuka HP. Kesadaran ini memberi ruang bagimu untuk mengambil jeda sejenak, mengamati dorongan tersebut, dan memilih respons yang lebih tenang dan bijaksana.
                    """.trimIndent(),
                    isRead = false,
                    imageResName = "satu"
                ),
                Article(
                    title = "Dopamin dan Kebiasaan Otomatis",
                    content = """
                        Ketika sebuah tindakan berulang kali memberikan dorongan dopamin dan kepuasan instan, otak kita membentuk jalur saraf baru yang semakin kuat. Proses ini mengubah tindakan yang tadinya butuh keputusan sadar menjadi sebuah kebiasaan otomatis yang berjalan di luar kendali pikiran rasional.

                        Struktur otak bernama basal ganglia bertanggung jawab menyimpan pola-pola kebiasaan otomatis ini. Saat kamu merasa bosan, cemas, atau lelah, basal ganglia akan langsung memicu dorongan refleks untuk meraih HP dan membuka aplikasi tertentu tanpa melibatkan peran prefrontal cortex yang berfungsi mengambil keputusan sadar.

                        Fenomena ini menjelaskan mengapa sering kali tanganmu sudah memegang HP dan membuka aplikasi tertentu sebelum kamu sempat berpikir mengapa kamu membukanya. Otak memilih jalur energi paling hemat dengan menjalankan kebiasaan otomatis yang sudah terpatri berulang kali.

                        Siklus kebiasaan (habit loop) ini terdiri dari tiga bagian: pemicu (cue), rutinitas (routine), dan imbalan (reward). Pemicunya bisa berupa emosi seperti kebosanan, rutinitasnya adalah membuka aplikasi, dan imbalannya adalah lonjakan dopamin singkat dari konten visual baru.

                        Untuk mengubah kebiasaan otomatis ini, melarang diri secara keras sering kali tidak efektif. Cara terbaik adalah menyelipkan jeda interupsi lembut di antara pemicu dan rutinitas. Jeda ini memberikan waktu bagi prefrontal cortex untuk bangun dan mengambil alih kembali kendali pikiranmu.
                    """.trimIndent(),
                    isRead = false,
                    imageResName = "dua"
                ),
                Article(
                    title = "Mengapa Kamu Scroll Tanpa Sadar",
                    content = """
                        Pernahkah kamu mengambil HP hanya untuk melihat jam atau membalas satu pesan singkat, namun tiba-tiba 30 menit kemudian kamu tersadar sedang scrolling feed atau video pendek tanpa tujuan? Kondisi ini dikenal dalam psikologi sebagai mode autopilot atau fenomena doomscrolling.

                        Aplikasi media sosial modern dirancang secara ilmiah menggunakan prinsip variable ratio reinforcement schedule — prinsip yang sama yang digunakan pada mesin slot di kasino. Karena kamu tidak pernah tahu konten apa yang akan muncul di scroll berikutnya, rasa penasaranmu terus terpicu.

                        Setiap swipe ke bawah bertindak seperti memutar tuas mesin slot. Sebagian besar konten mungkin biasa saja, namun sesekali kamu menemukan video yang lucu, informatif, atau mengejutkan. Ketidakpastian inilah yang secara biologis menjaga kadar dopamin tetap tinggi dan membuat jari terus bergeser.

                        Selain itu, fitur infinite scroll menghapus penanda alami penutup bab (cessation cues). Tidak seperti buku yang memiliki akhir bab atau surat kabar yang memiliki batas halaman, feed digital tidak pernah habis, sehingga otak tidak menerima sinyal biologis untuk berhenti.

                        Menyadari bahwa scrolling tanpa sadar adalah hasil dari desain antarmuka yang sengaja dirancang memikat akan membantumu bersikap lebih lembut pada diri sendiri. Ketika kamu tersadar sedang scrolling, tarik napas dalam, sadari momen tersebut, dan letakkan HP-mu tanpa penyesalan.
                    """.trimIndent(),
                    isRead = false,
                    imageResName = "tiga"
                ),
                Article(
                    title = "Apa itu Action Prediction Error?",
                    content = """
                        Neurosains klasik mempercayai bahwa otak belajar murni berdasarkan Reward Prediction Error (RPE), yaitu selisih antara imbalan yang diharapkan dengan imbalan nyata yang diterima. Namun riset neurosains terbaru dari University College London (UCL) menemukan mekanisme lain bernama Action Prediction Error (APE).

                        Sistem Action Prediction Error (APE) menjelaskan bahwa otak kita tidak hanya menghitung ganjaran imbalan, melainkan juga menghitung seberapa sering dan seberapa otomatis suatu aksi fisik diulangi. Setiap kali gerakan meraih HP dilakukan, otak memperkuat prediksi bahwa gerakan tersebut adalah default action.

                        Ini menjelaskan rahasia mengapa kamu tetap membuka aplikasi media sosial tertentu meskipun kamu sudah tahu isi aplikasinya membosankan atau membuatmu lelah. Kamu membukanya bukan lagi karena mengharapkan imbalan menarik, melainkan karena aksi tersebut telah menjadi default policy di otakmu.

                        Implikasi dari temuan APE sangat besar dalam membantu kita memahami adiksi digital. Kita sering kali menyalahkan diri sendiri karena merasa "kurang niat" atau "lemah iman", padahal otak hanya menjalankan aksi yang prediksinya sudah terbiasa berjalan secara fisik.

                        Dengan memahami APE, kita tahu bahwa kunci memutus rantai otomatis ini adalah memasukkan gesekan (friction) fisik atau jeda waktu sejenak sebelum aksi dilakukan. Gesekan kecil ini memutus prediksi otomatis otak dan mengembalikan ruang bagi keputusan sadar.
                    """.trimIndent(),
                    isRead = false,
                    imageResName = "empat"
                ),
                Article(
                    title = "Mengapa Memblokir Tidak Cukup",
                    content = """
                        Banyak orang mencoba mengendalikan penggunaan HP dengan menginstal aplikasi pemblokir ketat (hard blockers). Namun dalam banyak kasus, pemblokiran paksa ini hanya bertahan beberapa hari sebelum akhirnya pengguna mencari cara membuka sandi blokir atau merasa frustrasi berat.

                        Pendekatan pemblokiran paksa bekerja dengan prinsip hukuman dan larangan keras. Ketika prefrontal cortex sedang lelah di akhir hari, melarang diri secara ekstrem justru memicu reaksi penolakan dari otak bawah sadar, menciptakan rasa terkekang yang berujung pada pelampiasan berlebihan (rebound effect).

                        Selain itu, pemblokiran paksa tidak menyelesaikan akar masalah emosional yang memicu keinginan membuka HP. Jika rasa bosan, cemas, atau lelah yang mendasarinya belum disadari, otak akan selalu mencari jalan keluar lain untuk mendapatkan stimulasi atau pelarian.

                        Seremind percaya bahwa pendekatan yang lebih berkelanjutan adalah interupsi penuh kesadaran (mindful interruption). Alih-alih mengunci pintu secara paksa, kita menempatkan "gerbang refleksi" yang mengajakmu berhenti sejenak, bernapas, dan menyadari alasan di balik niatmu.

                        Ketika kamu diberi kebebasan untuk tetap membuka aplikasi setelah mengambil jeda reflektif, kamu sedang melatih otot kesadaranmu. Kendali diri yang dibangun dari dalam rasa paham jauh lebih kuat daripada kendali yang dipaksakan dari luar.
                    """.trimIndent(),
                    isRead = false,
                    imageResName = "lima"
                ),
                Article(
                    title = "Di Balik Satu Detik Sebelum Kamu Membuka HP",
                    content = """
                        Di antara kemunculan dorongan emosional dan tindakan fisik meraih HP, terdapat satu jendela waktu yang sangat singkat — yaitu satu detik emas kesadaran. Di dalam satu detik inilah arah keputusanmu ditentukan.

                        Pada satu detik tersebut, dorongan dari sistem limbik di otak baru saja terpicu oleh perasaan tertentu, seperti rasa sepi saat menunggu, rasa lelah di tengah pekerjaan, atau kejenuhan setelah fokus lama. Refleks tubuhmu biasanya langsung menjangkau HP secara otomatis.

                        Namun psikolog ternama Viktor Frankl pernah menulis: "Di antara stimulus dan respons, ada sebuah ruang. Di dalam ruang itulah terletak kebebasan dan kekuatan kita untuk memilih respons kita."

                        Jeda singkat yang dihadirkan Seremind dirancang khusus untuk memperluas ruang satu detik emas tersebut. Saat layar jeda muncul, waktu seakan melambat, memberimu kesempatan untuk bertanya: "Apa yang sebenarnya sedang aku butuhkan saat ini?"

                        Setiap kali kamu berhasil menyadari satu detik emas ini — apakah kamu akhirnya memilih tetap membuka aplikasi atau membatalkannya — kamu telah meraih kemenangan kecil dalam melatih kesadaran diri dan memegang kembali kemudi kehidupanmu.
                    """.trimIndent(),
                    isRead = false,
                    imageResName = "enam"
                ),
                Article(
                    title = "Emosi di Balik Kebiasaan",
                    content = """
                        Kebiasaan digital kita jarang sekali berdiri sendiri murni sebagai kebutuhan informasi. Di balik setiap gestur meraih HP dan membuka media sosial, hampir selalu terdapat pesan emosional mendalam yang sedang berusaha disampaikan oleh tubuh dan pikiranmu.

                        Penelitian menunjukkan bahwa pemicu terbesar penggunaan HP berlebihan adalah upaya regulasi emosi spontan. Ketika kita merasa lelah, cemas, canggung di keramaian, atau tertekan oleh beban kerja, HP menjadi benteng perlindungan instan untuk mengalihkan perhatian dari emosi tak nyaman tersebut.

                        Sayangnya, melarikan diri ke dalam layar tidak menghilangkan emosi dasar yang kita rasakan; itu hanya menundanya sementara. Ketika layar dimatikan, rasa cemas atau lelah tersebut sering kali kembali dengan intensitas yang sama atau bahkan lebih berat.

                        Melalui fitur pencatatan refleksi dan jurnal emosi, Seremind membimbingmu untuk belajar bersahabat dengan perasaanmu. Saat dorongan muncul, cobalah menamai emosimu: "Aku sedang merasa lelah", "Aku sedang merasa bosan", atau "Aku sedang butuh teman".

                        Ketika kamu mampu menerima dan memberi nama pada emosimu dengan penuh kasih sayang tanpa menghakimi, kebutuhan untuk melarikan diri ke layar HP akan berkurang dengan sendirinya. Kamu belajar menjadi tempat yang aman bagi dirimu sendiri.
                    """.trimIndent(),
                    isRead = false,
                    imageResName = "tujuh"
                )
            )

            for (article in articles) {
                articleDao.insert(article)
            }
        }
    }
}
