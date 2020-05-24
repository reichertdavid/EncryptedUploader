package rzx.wtf.encrypteduploader

import io.javalin.Javalin
import java.io.File
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object Uploader {

    /**
     * This is the hostname that'll be send as the destination link
     */
    const val HOSTNAME = "localhost"

    /**
     * I wouldn't recommend to run this on port 80 as default web-server so you can set a port here
     */
    const val PORT = 8080

    /**
     * This random instance is just to create a random file name
     */
    private val random = SecureRandom()

    @JvmStatic
    fun main(args: Array<String>) {
        val app = Javalin.create().start(8080)

        val fileFolder = File("files")

        // Creating files folder
        if (!fileFolder.exists()) {
            if (!fileFolder.mkdir()) {
                error("There was an error while creating a the files folder")
            }
        }

        // Listen for uploads
        app.post("/upload") { ctx ->
            val file = ctx.uploadedFile("image")
            if (file != null) {

                // encrypting image with aes
                val secretKey = KeyGenerator.getInstance("AES").generateKey()
                val encodedKey = Base64.getEncoder().encodeToString(secretKey.encoded).replace("=", "")
                val cipher = Cipher.getInstance("AES")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val bytes = cipher.doFinal(file.content.readBytes())

                // generating random filename
                val randomBytes = ByteArray(16)
                random.nextBytes(randomBytes)
                val fileName = Base64.getUrlEncoder().encodeToString(randomBytes).replace("=", "")

                // writing the file
                val writeFile = File(fileFolder, "$fileName")
                writeFile.writeBytes(bytes)

                // send response
                ctx.status(200)
                ctx.result("{ \"status\":200, \"data\": {\"link\": \"http://$HOSTNAME:$PORT/image/$fileName/$encodedKey\"} }")
                    .contentType("application/json")
            }
        }

        // Listen for image views
        app.get("/image/:name/:key") { ctx ->
            val fileName = ctx.pathParam("name")
            val encodedKey = ctx.pathParam("key")
            val file = File(fileFolder, "$fileName")

            if (file.exists()) {
                val decodedKey: ByteArray = Base64.getDecoder().decode(encodedKey)
                val originalKey: SecretKey = SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
                val cipher = Cipher.getInstance("AES")
                cipher.init(Cipher.DECRYPT_MODE, originalKey)

                try {
                    val bytes = cipher.doFinal(file.readBytes())

                    ctx.status(200)
                    ctx.result(bytes)
                } catch (e: Exception) {
                    ctx.status(401)
                    ctx.result("Invalid key.")
                }
            } else {
                ctx.status(404)
                ctx.result("File not found.")
            }
        }
    }

}