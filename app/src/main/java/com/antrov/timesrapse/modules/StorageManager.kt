package com.antrov.timesrapse.modules

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.elvishew.xlog.XLog
import org.koin.core.component.KoinApiExtension
import org.koin.core.component.KoinComponent
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

interface StorageManager {
    fun store(buffer: ByteBuffer)
    fun catalogStats(): Pair<Int, Long>
}

@KoinApiExtension
class StorageManagerImpl : StorageManager, KoinComponent {

    private val logger = XLog.tag("storage").build()

    private val path = Environment.getExternalStorageDirectory().toString() + "/Pictures/"

    override fun catalogStats(): Pair<Int, Long> {
        val dir = File(path)
        val files = dir.listFiles() ?: return Pair(0, 0)

        var length: Long = 0
        for (file in files) {
            length += if (file.isFile) file.length() else 0
        }

        return Pair(files.size, length)
    }

    override fun store(buffer: ByteBuffer) {
        val bytes: ByteArray

        buffer.apply {
            bytes = ByteArray(remaining())
            get(bytes)
        }

        Thread(Runnable {
            val name = (System.currentTimeMillis() / 1000L).toString() + ".webp"
            val file = File(path + name)

            var output: FileOutputStream? = null

            val bitmap: Bitmap?

            logger.d("writing to file $path$name")

            try {
                output = FileOutputStream(file)
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bitmap?.compress(Bitmap.CompressFormat.WEBP, 80, output)
                bitmap?.recycle()
                logger.d("image written")

                catalogStats().let {
                    logger.i("files: %d, size: %dMB", it.first, it.second / 1024 / 2014)
                }
            } catch (e: FileNotFoundException) {
                logger.e("file $path$name not found", e)
            } catch (e: IOException) {
                logger.e( "i/o", e)
            } finally {
                output?.apply {
                    try {
                        close()
                    } catch (e: Exception) {
                        logger.e( "output close", e)
                    }
                }
            }
        }).start()
    }


}