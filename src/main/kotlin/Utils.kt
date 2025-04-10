package com.example

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

object Utils {
    fun extractFileContentFromTar(
        tarInputStream: TarArchiveInputStream,
        shouldExtract: (String) -> Boolean
    ): String? {
        tarInputStream.use { stream ->
            var entry: TarArchiveEntry? = stream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && shouldExtract(entry.name)) {
                    return stream.readBytes().toString(Charsets.UTF_8)
                }
                entry = stream.nextEntry
            }
        }
        return null
    }
}


