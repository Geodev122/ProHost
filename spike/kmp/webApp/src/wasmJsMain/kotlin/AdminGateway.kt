import firebase.FirebaseApp
import firebase.getAuth
import firebase.getFunctions
import firebase.httpsCallable
import firebase.initializeApp

/**
 * What the web admin console needs from Firebase, as a Kotlin interface so the screens never see JavaScript
 * (the same seam idea as AuthGateway / FunctionsClient on Android and iOS).
 */
interface AdminGateway {
    val appName: String
    val isSignedIn: Boolean
    fun callableExists(name: String): Boolean
}

private fun dummyConfig(): JsAny = js("({ apiKey: 'spike-not-a-real-key', authDomain: 'spike.invalid', projectId: 'demo-prohost', appId: '1:0:web:spike' })")

class FirebaseWebGateway : AdminGateway {
    private val app: FirebaseApp = initializeApp(dummyConfig())
    private val auth = getAuth(app)
    private val functions = getFunctions(app, "europe-west1") // region of every ProHost callable

    override val appName: String get() = app.name
    override val isSignedIn: Boolean get() = auth.currentUser != null
    override fun callableExists(name: String): Boolean = httpsCallable(functions, name) != null
}
