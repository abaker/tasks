package org.tasks.etesync

import android.content.Context
import androidx.core.util.Pair
import at.bitfire.cert4android.CustomCertManager
import com.etesync.journalmanager.*
import com.etesync.journalmanager.Constants.Companion.CURRENT_VERSION
import com.etesync.journalmanager.Crypto.AsymmetricKeyPair
import com.etesync.journalmanager.Crypto.CryptoManager
import com.etesync.journalmanager.Exceptions.IntegrityException
import com.etesync.journalmanager.Exceptions.VersionTooNewException
import com.etesync.journalmanager.JournalManager.Journal
import com.etesync.journalmanager.UserInfoManager.UserInfo.Companion.generate
import com.etesync.journalmanager.model.CollectionInfo
import com.etesync.journalmanager.model.CollectionInfo.Companion.fromJson
import com.etesync.journalmanager.model.SyncEntry
import com.etesync.journalmanager.util.TokenAuthenticator
import com.google.common.collect.Lists
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.internal.tls.OkHostnameVerifier
import org.tasks.DebugNetworkInterceptor
import org.tasks.caldav.MemoryCookieStore
import org.tasks.data.CaldavAccount
import org.tasks.data.CaldavCalendar
import org.tasks.preferences.Preferences
import org.tasks.security.KeyStoreEncryption
import timber.log.Timber
import java.io.IOException
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLContext

@Deprecated("use etebase")
class EteSyncClient {
    private val encryption: KeyStoreEncryption
    private val preferences: Preferences
    private val interceptor: DebugNetworkInterceptor
    private val username: String?
    private val token: String?
    private val encryptionPassword: String?
    private val httpClient: OkHttpClient?
    private val httpUrl: HttpUrl?
    private val context: Context
    private val journalManager: JournalManager?
    private var foreground = false

    @Inject
    constructor(
            @ApplicationContext context: Context,
            encryption: KeyStoreEncryption,
            preferences: Preferences,
            interceptor: DebugNetworkInterceptor) {
        this.context = context
        this.encryption = encryption
        this.preferences = preferences
        this.interceptor = interceptor
        username = null
        token = null
        encryptionPassword = null
        httpClient = null
        httpUrl = null
        journalManager = null
    }

    private constructor(
            context: Context,
            encryption: KeyStoreEncryption,
            preferences: Preferences,
            interceptor: DebugNetworkInterceptor,
            url: String?,
            username: String?,
            encryptionPassword: String?,
            token: String?,
            foreground: Boolean) {
        this.context = context
        this.encryption = encryption
        this.preferences = preferences
        this.interceptor = interceptor
        this.username = username
        this.encryptionPassword = encryptionPassword
        this.token = token
        this.foreground = foreground
        val customCertManager = CustomCertManager(context)
        customCertManager.appInForeground = foreground
        val hostnameVerifier = customCertManager.hostnameVerifier(OkHostnameVerifier)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(customCertManager), null)
        val builder = OkHttpClient()
                .newBuilder()
                .addNetworkInterceptor(TokenAuthenticator(null, token))
                .cookieJar(MemoryCookieStore())
                .followRedirects(false)
                .followSslRedirects(true)
                .sslSocketFactory(sslContext.socketFactory, customCertManager)
                .hostnameVerifier(hostnameVerifier)
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
        if (preferences.isFlipperEnabled) {
            interceptor.apply(builder)
        }
        httpClient = builder.build()
        httpUrl = url?.toHttpUrlOrNull()
        journalManager = JournalManager(httpClient, httpUrl!!)
    }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    suspend fun forAccount(account: CaldavAccount): EteSyncClient {
        return forUrl(
                account.url,
                account.username,
                account.getEncryptionPassword(encryption),
                account.getPassword(encryption))
    }

    @Throws(KeyManagementException::class, NoSuchAlgorithmException::class)
    suspend fun forUrl(url: String?, username: String?, encryptionPassword: String?, token: String?): EteSyncClient = withContext(Dispatchers.IO) {
        EteSyncClient(
                context,
                encryption,
                preferences,
                interceptor,
                url,
                username,
                encryptionPassword,
                token,
                foreground)
    }

    @Throws(IOException::class, Exceptions.HttpException::class)
    suspend fun getToken(password: String?): String? = withContext(Dispatchers.IO) {
        JournalAuthenticator(httpClient!!, httpUrl!!).getAuthToken(username!!, password!!)
    }

    @Throws(Exceptions.HttpException::class)
    suspend fun userInfo(): UserInfoManager.UserInfo? = withContext(Dispatchers.IO) {
        val userInfoManager = UserInfoManager(httpClient!!, httpUrl!!)
        userInfoManager.fetch(username!!)
    }

    @Throws(VersionTooNewException::class, IntegrityException::class)
    fun getCrypto(userInfo: UserInfoManager.UserInfo?, journal: Journal): CryptoManager {
        if (journal.key == null) {
            return CryptoManager(journal.version, encryptionPassword!!, journal.uid!!)
        }
        if (userInfo == null) {
            throw RuntimeException("Missing userInfo")
        }
        val cryptoManager = CryptoManager(userInfo.version!!.toInt(), encryptionPassword!!, "userInfo")
        val keyPair = AsymmetricKeyPair(userInfo.getContent(cryptoManager)!!, userInfo.pubkey!!)
        return CryptoManager(journal.version, keyPair, journal.key!!)
    }

    private fun convertJournalToCollection(userInfo: UserInfoManager.UserInfo?, journal: Journal): CollectionInfo? {
        return try {
            val cryptoManager = getCrypto(userInfo, journal)
            journal.verify(cryptoManager)
            val collection = fromJson(journal.getContent(cryptoManager))
            collection.updateFromJournal(journal)
            collection
        } catch (e: IntegrityException) {
            Timber.e(e)
            null
        } catch (e: VersionTooNewException) {
            Timber.e(e)
            null
        }
    }

    @Throws(Exceptions.HttpException::class)
    suspend fun getCalendars(userInfo: UserInfoManager.UserInfo?): Map<Journal, CollectionInfo> = withContext(Dispatchers.IO) {
        val result: MutableMap<Journal, CollectionInfo> = HashMap()
        for (journal in journalManager!!.list()) {
            val collection = convertJournalToCollection(userInfo, journal)
            if (collection != null) {
                if (TYPE_TASKS == collection.type) {
                    Timber.v("Found %s", collection)
                    result[journal] = collection
                } else {
                    Timber.v("Ignoring %s", collection)
                }
            }
        }
        result
    }

    @Throws(IntegrityException::class, Exceptions.HttpException::class, VersionTooNewException::class)
    suspend fun getSyncEntries(
            userInfo: UserInfoManager.UserInfo?,
            journal: Journal,
            calendar: CaldavCalendar,
            callback: suspend (List<Pair<JournalEntryManager.Entry, SyncEntry>>) -> Unit) = withContext(Dispatchers.IO) {
        val journalEntryManager = JournalEntryManager(httpClient!!, httpUrl!!, journal.uid!!)
        val crypto = getCrypto(userInfo, journal)
        var journalEntries: List<JournalEntryManager.Entry>
        do {
            journalEntries = journalEntryManager.list(crypto, calendar.ctag, MAX_FETCH)
            callback(journalEntries.map {
                Pair.create(it, SyncEntry.fromJournalEntry(crypto, it))
            })
        } while (journalEntries.size >= MAX_FETCH)
    }

    @Throws(Exceptions.HttpException::class)
    suspend fun pushEntries(journal: Journal, entries: List<JournalEntryManager.Entry>?, remoteCtag: String?) = withContext(Dispatchers.IO) {
        var remoteCtag = remoteCtag
        val journalEntryManager = JournalEntryManager(httpClient!!, httpUrl!!, journal.uid!!)
        for (partition in Lists.partition(entries!!, MAX_PUSH)) {
            journalEntryManager.create(partition, remoteCtag)
            remoteCtag = partition[partition.size - 1].uid
        }
    }

    fun setForeground() {
        foreground = true
    }

    suspend fun invalidateToken() = withContext(Dispatchers.IO) {
        try {
            JournalAuthenticator(httpClient!!, httpUrl!!).invalidateAuthToken(token!!)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    @Throws(VersionTooNewException::class, IntegrityException::class, Exceptions.HttpException::class)
    suspend fun makeCollection(name: String?, color: Int): String = withContext(Dispatchers.IO) {
        val uid = Journal.genUid()
        val collectionInfo = CollectionInfo()
        collectionInfo.displayName = name
        collectionInfo.type = TYPE_TASKS
        collectionInfo.uid = uid
        collectionInfo.selected = true
        collectionInfo.color = if (color == 0) null else color
        val crypto = CryptoManager(collectionInfo.version, encryptionPassword!!, uid)
        journalManager!!.create(Journal(crypto, collectionInfo.toJson(), uid))
        uid
    }

    @Throws(VersionTooNewException::class, IntegrityException::class, Exceptions.HttpException::class)
    suspend fun updateCollection(calendar: CaldavCalendar, name: String?, color: Int): String? = withContext(Dispatchers.IO) {
        val uid = calendar.url
        val journal = journalManager!!.fetch(uid!!)
        val userInfo = userInfo()
        val crypto = getCrypto(userInfo, journal)
        val collectionInfo = convertJournalToCollection(userInfo, journal)
        collectionInfo!!.displayName = name
        collectionInfo.color = if (color == 0) null else color
        journalManager.update(Journal(crypto, collectionInfo.toJson(), uid))
        uid
    }

    @Throws(Exceptions.HttpException::class)
    suspend fun deleteCollection(calendar: CaldavCalendar) = withContext(Dispatchers.IO) {
        journalManager!!.delete(Journal.fakeWithUid(calendar.url!!))
    }

    @Throws(Exceptions.HttpException::class, VersionTooNewException::class, IntegrityException::class, IOException::class)
    suspend fun createUserInfo(derivedKey: String?) = withContext(Dispatchers.IO) {
        val cryptoManager = CryptoManager(CURRENT_VERSION, derivedKey!!, "userInfo")
        val userInfo: UserInfoManager.UserInfo = generate(cryptoManager, username!!)
        UserInfoManager(httpClient!!, httpUrl!!).create(userInfo)
    }

    companion object {
        private const val TYPE_TASKS = "TASKS"
        private const val MAX_FETCH = 50
        private const val MAX_PUSH = 30
    }
}