package pl.datart.csft.encryption

import cats.implicits._
import cats.effect._
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.io._
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings._
import org.bouncycastle.crypto.params._
import org.bouncycastle.jce.provider.BouncyCastleProvider

import java.io._
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.security.{SecureRandom, Security}

trait Encryption[F[_]] {
  def encrypt(inputStream: InputStream, passphrase: String): F[ByteArrayOutputStream]
  def encryptFile(fileIn: File, filePathOut: String, passphrase: String): F[Unit]
  def decrypt(inputStream: InputStream, passphrase: String): F[ByteArrayOutputStream]
  def decryptFile(fileIn: File, filePathOut: String, passphrase: String): F[Unit]
}

object Encryption {
  def aesEncryptionInstance[F[_]](implicit async: Async[F]): F[Encryption[F]] = {
    new EncryptionAESImpl[F]().initBouncyCastle()
  }

  private class EncryptionAESImpl[F[_]](implicit async: Async[F]) extends Encryption[F] {
    private val iterations = 12000 // the number of times the password gets hashed
    private val keyLength  = 256

    // Password Based Encryption using Sha256
    // + 256-bit AES
    // + CBC + BC which means that the encryption is done in distinct blocks
    private val algorithm = "PBEWITHSHA256AND256BITAES-CBC-BC"

    private val aesIVBitsCount = 128 // the length of Initialization Vector
    private val salt           = Array[Byte](104, -99, -101, 44, 83, -122, 4, 26, -77, 83, -110, 17, 103, 2, 22, -73, -27)

    def encrypt(inputStream: InputStream, passphrase: String): F[ByteArrayOutputStream] = {
      async.delay {
        val ivData =
          new Array[Byte](
            aesIVBitsCount / 8
          )                                              // initializing an Initialization Vector that is used to initiate the block cipher
        SecureRandom.getInstanceStrong.nextBytes(ivData) // content of IV comes from an instance of SecureRandom

        // selecting encryption algorithm and padding
        // specifying encryption of an input stream using key+IV
        val keyParam = getKeyFromPassphrase(passphrase)
        val params   = new ParametersWithIV(keyParam, ivData)
        val padding  = new PKCS7Padding()
        // setting up the BlockCipher, buffering to handle big files
        val cipher   = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), padding)

        cipher.reset()
        cipher.init(true, params) // setting cipher to perform encryption

        val bos = new ByteArrayOutputStream()
        bos.write(ivData) // saving IV in the beginning of the output stream in for decrypting purposes

        //finally encrypting an input stream
        val cipherOut = new CipherOutputStream(bos, cipher)
        val _         = inputStream.transferTo(cipherOut)

        cipherOut.close()
        bos
      }
    }

    def encryptFile(fileIn: File, filePathOut: String, passphrase: String): F[Unit] = {
      async.defer {
        val inputStream      = new FileInputStream(fileIn)
        val fileOutputStream = new FileOutputStream(filePathOut)
        encrypt(inputStream, passphrase).map { bos =>
          bos.writeTo(fileOutputStream)
          bos.close()
          fileOutputStream.flush()
          fileOutputStream.close()
        }
      }
    }

    def decrypt(encStream: InputStream, passphrase: String): F[ByteArrayOutputStream] = {
      async.delay {
        // extracting the Initialization Vector, which is stored in the first N bytes at the start of stream
        val nIvBytes = aesIVBitsCount / 8
        val ivBytes  = new Array[Byte](nIvBytes)
        val _        = encStream.read(ivBytes, 0, nIvBytes)

        // similar as for encryption
        val keyParam = getKeyFromPassphrase(passphrase)
        val params   = new ParametersWithIV(keyParam, ivBytes)
        val padding  = new PKCS7Padding()
        val cipher   = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), padding)

        cipher.reset()
        cipher.init(false, params) // setting cipher to perform decryption

        val bos = new ByteArrayOutputStream()

        // decrypting an input stream
        val cipherIn = new CipherInputStream(encStream, cipher)
        val _        = cipherIn.transferTo(bos)

        cipherIn.close()
        bos
      }
    }

    def decryptFile(fileIn: File, filePathOut: String, passphrase: String): F[Unit] = {
      async.defer {
        val inputStream      = new FileInputStream(fileIn)
        val fileOutputStream = new FileOutputStream(filePathOut)
        decrypt(inputStream, passphrase).map { bos =>
          bos.writeTo(fileOutputStream)
          bos.close()
          fileOutputStream.flush()
          fileOutputStream.close()
        }
      }
    }

    private[encryption] def initBouncyCastle(): F[Encryption[F]] = {
      async
        .delay {
          val _ = Security.addProvider(new BouncyCastleProvider)
          Option(Security.getProvider("BC"))
        }
        .flatMap {
          case Some(_) =>
            async.delay(this)
          case _       =>
            async.raiseError(new IllegalStateException("Could not create Bouncy Castle provider"))
        }
    }

    // A function to convert string user provided password into an encryption key for Password Based Encryption.
    private def getKeyFromPassphrase(passphrase: String): KeyParameter = {
      val keySpec =
        new PBEKeySpec(
          passphrase.toCharArray,
          salt,
          iterations,
          keyLength
        ) // setting up the KeySpec using the password and salt
      val keyFactory =
        SecretKeyFactory.getInstance(algorithm) // setting up the encryption factory using the specified algorithm
      val rawKey = keyFactory.generateSecret(keySpec).getEncoded // generating the key as a byte array

      new KeyParameter(rawKey)
    }
  }
}
