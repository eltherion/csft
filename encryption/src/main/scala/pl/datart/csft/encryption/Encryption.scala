package pl.datart.csft.encryption

import cats.implicits._
import cats.effect._
import org.apache.commons.io.IOUtils
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
    private val iterations     = 12000
    private val keyLength      = 256
    private val algorithm      = "PBEWITHSHA256AND256BITAES-CBC-BC"
    private val aesIVBitsCount = 128
    private val salt           = Array[Byte](104, -99, -101, 44, 83, -122, 4, 26, -77, 83, -110, 17, 103, 2, 22, -73, -27)

    def encrypt(inputStream: InputStream, passphrase: String): F[ByteArrayOutputStream] = {
      async.delay {
        val ivData = new Array[Byte](aesIVBitsCount / 8)
        SecureRandom.getInstanceStrong.nextBytes(ivData)

        //Select encrypt algo and padding: AES with CBC and PCKS7.
        //Encrypt input stream using key+iv.
        val keyParam = getKeyFromPassphrase(passphrase)
        val params   = new ParametersWithIV(keyParam, ivData)

        val padding = new PKCS7Padding()
        val cipher  = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), padding)

        cipher.reset()
        cipher.init(true, params)

        val bos       = new ByteArrayOutputStream()
        bos.write(ivData)
        val cipherOut = new CipherOutputStream(bos, cipher)
        val _         = IOUtils.copy(inputStream, cipherOut)

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
        //Extract the IV, which si stored in the next N bytes at the start of stream.
        val nIvBytes = aesIVBitsCount / 8
        val ivBytes  = new Array[Byte](nIvBytes)
        val _        = encStream.read(ivBytes, 0, nIvBytes)

        val keyParam = getKeyFromPassphrase(passphrase)
        val params   = new ParametersWithIV(keyParam, ivBytes)
        val padding  = new PKCS7Padding()
        val cipher   = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()), padding)

        cipher.reset()
        cipher.init(false, params)

        val bos      = new ByteArrayOutputStream()
        val cipherIn = new CipherInputStream(encStream, cipher)
        val _        = IOUtils.copy(cipherIn, bos)

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

    private def getKeyFromPassphrase(passphrase: String): KeyParameter = {
      val keySpec    = new PBEKeySpec(passphrase.toCharArray, salt, iterations, keyLength)
      val keyFactory = SecretKeyFactory.getInstance(algorithm)
      val rawKey     = keyFactory.generateSecret(keySpec).getEncoded

      new KeyParameter(rawKey)
    }
  }
}
