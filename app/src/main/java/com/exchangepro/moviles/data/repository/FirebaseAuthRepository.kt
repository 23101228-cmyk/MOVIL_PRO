package com.exchangepro.moviles.data.repository

import com.exchangepro.moviles.data.firebase.FirebaseCollections
import com.exchangepro.moviles.domain.model.CurrencyCode
import com.exchangepro.moviles.domain.model.UserRole
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

data class RegistrationData(
    val names: String,
    val lastNames: String,
    val email: String,
    val phone: String,
    val documentNumber: String,
    val password: String
)

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /**
     * Autentica credenciales y luego lee users/{uid}; Authentication identifica a la
     * persona y Firestore aporta el rol que controla la navegacion USER/ADMIN.
     */
    fun signIn(email: String, password: String, onResult: (Result<UserRole>) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid == null) {
                    auth.signOut()
                    onResult(Result.failure(IllegalStateException("No se pudo identificar al usuario.")))
                    return@addOnSuccessListener
                }

                db.collection(FirebaseCollections.USERS)
                    .document(uid)
                    .get()
                    .addOnSuccessListener { document ->
                        val role = document.getString("role")
                            ?.uppercase()
                            ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }

                        if (!document.exists() || role == null) {
                            auth.signOut()
                            onResult(Result.failure(IllegalStateException("La cuenta no tiene un perfil o rol valido.")))
                        } else {
                            onResult(Result.success(role))
                        }
                    }
                    .addOnFailureListener { error ->
                        auth.signOut()
                        onResult(Result.failure(readableError(error)))
                    }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(readableError(error)))
            }
    }

    /**
     * Crea la identidad y, en un batch, su perfil, wallet y balances iniciales.
     * Ante un fallo del batch elimina la identidad para evitar cuentas incompletas.
     */
    fun register(data: RegistrationData, onResult: (Result<Unit>) -> Unit) {
        auth.createUserWithEmailAndPassword(data.email.trim(), data.password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid
                if (user == null || uid == null) {
                    auth.signOut()
                    onResult(Result.failure(IllegalStateException("No se pudo crear la cuenta.")))
                    return@addOnSuccessListener
                }

                val userRef = db.collection(FirebaseCollections.USERS).document(uid)
                val walletRef = db.collection(FirebaseCollections.WALLETS).document(uid)
                val batch = db.batch()

                batch.set(
                    userRef,
                    mapOf(
                        "id" to uid,
                        "role" to UserRole.USER.name,
                        "names" to data.names.trim(),
                        "lastNames" to data.lastNames.trim(),
                        "fullName" to "${data.names.trim()} ${data.lastNames.trim()}",
                        "email" to data.email.trim().lowercase(),
                        "phone" to data.phone,
                        "documentNumber" to data.documentNumber,
                        "reputation" to 5.0,
                        "totalRatings" to 0,
                        "status" to "ACTIVO",
                        "photoUrl" to null,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                CurrencyCode.entries.forEach { currency ->
                    batch.set(
                        walletRef.collection(FirebaseCollections.BALANCES).document(currency.name),
                        mapOf(
                            "currency" to currency.name,
                            "available" to 0.0,
                            "retained" to 0.0
                        )
                    )
                }

                batch.commit()
                    .addOnSuccessListener {
                        auth.signOut()
                        onResult(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        user.delete().addOnCompleteListener {
                            auth.signOut()
                            onResult(Result.failure(readableError(error)))
                        }
                    }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(readableError(error)))
            }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { error -> onResult(Result.failure(readableError(error))) }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun readableError(error: Exception): Exception {
        val message = when (error) {
            is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo."
            is FirebaseAuthInvalidCredentialsException -> "El correo o la contrasena son incorrectos."
            is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo."
            is FirebaseAuthWeakPasswordException -> "La contrasena no cumple los requisitos de seguridad."
            is FirebaseNetworkException -> "No se pudo conectar con Firebase. Revisa tu conexion."
            else -> error.localizedMessage ?: "Ocurrio un error al comunicarse con Firebase."
        }
        return IllegalStateException(message, error)
    }
}
