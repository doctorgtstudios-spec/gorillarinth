package com.gorillarinth.app

import android.content.Context
import android.os.Build
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.value.DataType
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Date
import java.util.Enumeration
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * On-device QuestLoader APK patcher. This intentionally targets the legacy QuestLoader
 * loader pair published by sc2ad. It does not pretend to patch Scotland2 APKs.
 */
object QuestLoaderApkPatcher {
    private const val PACKAGE = "com.AnotherAxiom.GorillaTag"
    private const val RELEASE = "v1.3.0"
    private const val BASE_URL = "https://github.com/sc2ad/QuestLoader/releases/download/$RELEASE/"

    fun patch(context: Context, sourceApk: File): File {
        require(sourceApk.isFile && sourceApk.length() > 1024 * 1024) { "Import or download a complete Gorilla Tag APK first." }
        val info = context.packageManager.getPackageArchiveInfo(sourceApk.absolutePath, 0)
            ?: error("The selected file is not a readable APK.")
        require(info.packageName == PACKAGE) { "This patcher only accepts the official Gorilla Tag package." }

        val abi = selectAbi(sourceApk)
        val is64 = abi == "arm64-v8a"
        val loaderMain = downloadLoader(context, if (is64) "libmain64.so" else "libmain32.so", is64)
        val loaderMod = downloadLoader(context, if (is64) "libmodloader64.so" else "libmodloader32.so", is64)

        val work = File(context.cacheDir, "gorillarinth-patch").apply { mkdirs() }
        val manifestPatched = File(work, "manifest-patched.apk")
        val unsigned = File(work, "gorillarinth-unsigned.apk")
        val signedTemp = File(context.filesDir, "gorilla_tag_patched.apk.tmp")
        val output = File(context.filesDir, "gorilla_tag_patched.apk")
        manifestPatched.delete()
        unsigned.delete()
        signedTemp.delete()

        val module = ApkModule.loadApkFile(sourceApk)
        try {
            val manifest = module.androidManifestBlock
            require(manifest.packageName == PACKAGE) { "APK manifest package does not match Gorilla Tag." }
            manifest.addUsesPermission("android.permission.READ_EXTERNAL_STORAGE")
            manifest.addUsesPermission("android.permission.WRITE_EXTERNAL_STORAGE")
            val application = manifest.getOrCreateApplicationElement()
            application.getOrCreateAndroidAttribute("requestLegacyExternalStorage", 16844291).apply {
                setDataType(DataType.BOOLEAN)
                setData(-1)
            }
            // Compressed native libraries are extracted by PackageInstaller when this flag is set.
            application.getOrCreateAndroidAttribute("extractNativeLibs", 16844010).apply {
                setDataType(DataType.BOOLEAN)
                setData(-1)
            }
            module.writeApk(manifestPatched)
        } finally {
            runCatching { module.close() }
        }

        rewriteZipWithLoader(manifestPatched, unsigned, abi, loaderMain, loaderMod)
        signApk(context, unsigned, signedTemp)
        val verification = ApkVerifier.Builder(signedTemp).build().verify()
        require(verification.isVerified) {
            "The patched APK signature could not be verified: " +
                verification.errors.joinToString("; ") { it.toString() }
        }
        val patchedInfo = context.packageManager.getPackageArchiveInfo(signedTemp.absolutePath, 0)
            ?: error("Android could not read the patched APK after signing.")
        require(patchedInfo.packageName == PACKAGE) { "The patched APK package changed unexpectedly." }
        if (output.exists()) output.delete()
        check(signedTemp.renameTo(output)) { "Could not save the patched Gorilla Tag APK." }
        manifestPatched.delete()
        unsigned.delete()
        return output
    }

    private fun selectAbi(apk: File): String {
        val zip = ZipFile(apk)
        zip.use {
            val available = Build.SUPPORTED_ABIS.toList()
            for (abi in available) {
                if (abi != "arm64-v8a" && abi != "armeabi-v7a") continue
                if (zip.getEntry("lib/$abi/libmain.so") != null) return abi
            }
        }
        error("This Gorilla Tag APK has no supported ARM Unity libmain.so. Only arm64-v8a and armeabi-v7a are supported.")
    }

    private fun downloadLoader(context: Context, name: String, is64: Boolean): File {
        val folder = File(context.filesDir, "questloader/$RELEASE").apply { mkdirs() }
        val target = File(folder, name)
        if (target.isFile && target.length() > 1024) {
            verifyElf(target, is64)
            return target
        }
        val temp = File(folder, "$name.tmp")
        val connection = URL(BASE_URL + name).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", "GorillaRinth/0.1")
            require(connection.responseCode in 200..299) { "Could not download QuestLoader $name (HTTP ${connection.responseCode})." }
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            verifyElf(temp, is64)
            if (target.exists()) target.delete()
            check(temp.renameTo(target)) { "Could not cache QuestLoader $name." }
            return target
        } finally {
            connection.disconnect()
            temp.delete()
        }
    }

    private fun verifyElf(file: File, is64: Boolean) {
        val header = ByteArray(20)
        file.inputStream().use { input ->
            require(input.read(header) == header.size) { "QuestLoader binary is truncated: ${file.name}." }
        }
        require(header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) {
            "QuestLoader download is not an ELF library: ${file.name}."
        }
        val elfClass = header[4].toInt() and 0xff
        val machine = (header[18].toInt() and 0xff) or ((header[19].toInt() and 0xff) shl 8)
        require((if (is64) elfClass == 2 && machine == 183 else elfClass == 1 && machine == 40)) {
            "QuestLoader binary architecture does not match the Gorilla Tag APK."
        }
    }

    private fun rewriteZipWithLoader(
        inputApk: File,
        outputApk: File,
        abi: String,
        loaderMain: File,
        loaderMod: File
    ) {
        val mainPath = "lib/$abi/libmain.so"
        val modPath = "lib/$abi/libmodloader.so"
        val zip = ZipFile(inputApk)
        var foundMain = false
        zip.use { source ->
            ZipOutputStream(FileOutputStream(outputApk)).use { destination ->
                val entries: Enumeration<out ZipEntry> = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (isSignatureFile(entry.name)) continue
                    if (entry.name == mainPath) {
                        addFile(destination, mainPath, loaderMain)
                        foundMain = true
                        continue
                    }
                    if (entry.name == modPath) continue
                    val copy = ZipEntry(entry.name).apply {
                        time = entry.time
                        comment = entry.comment
                        if (entry.method == ZipEntry.STORED) {
                            method = ZipEntry.STORED
                            size = entry.size
                            compressedSize = entry.size
                            crc = entry.crc
                        } else {
                            method = ZipEntry.DEFLATED
                        }
                    }
                    destination.putNextEntry(copy)
                    source.getInputStream(entry).use { it.copyTo(destination) }
                    destination.closeEntry()
                }
                require(foundMain) { "The APK does not contain $mainPath; it cannot be patched by QuestLoader." }
                addFile(destination, modPath, loaderMod)
            }
        }
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun isSignatureFile(name: String): Boolean {
        if (!name.startsWith("META-INF/", ignoreCase = true)) return false
        val leaf = name.substringAfterLast('/').uppercase()
        return leaf.endsWith(".SF") || leaf.endsWith(".RSA") ||
            leaf.endsWith(".DSA") || leaf.endsWith(".EC") || leaf == "MANIFEST.MF"
    }

    private fun signApk(context: Context, input: File, output: File) {
        val alias = "gorillarinth-apk-signing-v1"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val spec: AlgorithmParameterSpec = KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=GorillaRinth APK Patcher"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(0L))
                .setCertificateNotAfter(Date(System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000))
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val certificate = keyStore.getCertificate(alias) as X509Certificate
        val signer = ApkSigner.SignerConfig.Builder("GorillaRinth", privateKey, listOf(certificate)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setV4SigningEnabled(false)
            .build()
            .sign()
    }
}
