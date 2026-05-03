package top.imsyy.splayer.nativeapp.data.repository

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

interface NativeQrCodeGenerator {
    fun toPngDataUri(content: String): String
}

class ZxingNativeQrCodeGenerator : NativeQrCodeGenerator {
    override fun toPngDataUri(content: String): String {
        val size = 512
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
            ),
        )
        val png = encodeMonochromePng(
            width = size,
            height = size,
            isBlack = { x, y -> matrix[x, y] },
        )
        val encoded = Base64.getEncoder().encodeToString(png)
        return "data:image/png;base64,$encoded"
    }
}

private fun encodeMonochromePng(
    width: Int,
    height: Int,
    isBlack: (Int, Int) -> Boolean,
): ByteArray {
    val raw = ByteArrayOutputStream()
    for (y in 0 until height) {
        raw.write(0)
        for (x in 0 until width) {
            val value = if (isBlack(x, y)) 0 else 255
            raw.write(value)
            raw.write(value)
            raw.write(value)
        }
    }
    val compressed = ByteArrayOutputStream()
    DeflaterOutputStream(compressed, Deflater(Deflater.BEST_SPEED)).use { stream ->
        stream.write(raw.toByteArray())
    }
    return ByteArrayOutputStream().apply {
        write(PNG_SIGNATURE)
        writeChunk("IHDR", buildByteArray {
            writeInt(width)
            writeInt(height)
            write(8)
            write(2)
            write(0)
            write(0)
            write(0)
        })
        writeChunk("IDAT", compressed.toByteArray())
        writeChunk("IEND", ByteArray(0))
    }.toByteArray()
}

private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
    writeInt(data.size)
    val typeBytes = type.toByteArray(Charsets.US_ASCII)
    write(typeBytes)
    write(data)
    val crc = CRC32()
    crc.update(typeBytes)
    crc.update(data)
    writeInt(crc.value.toInt())
}

private fun ByteArrayOutputStream.writeInt(value: Int) {
    write((value ushr 24) and 0xff)
    write((value ushr 16) and 0xff)
    write((value ushr 8) and 0xff)
    write(value and 0xff)
}

private fun buildByteArray(block: ByteArrayOutputStream.() -> Unit): ByteArray {
    return ByteArrayOutputStream().apply(block).toByteArray()
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)
