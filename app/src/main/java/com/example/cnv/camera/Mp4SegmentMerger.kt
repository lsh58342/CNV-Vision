package com.example.cnv.camera

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Concatenates same-codec MP4 segments (CameraX rolling buffer) into one clip.
 */
object Mp4SegmentMerger {

    fun merge(segments: List<File>, output: File): Boolean {
        val inputs = segments.filter { it.exists() && it.length() > 0L }
        if (inputs.isEmpty()) return false
        if (inputs.size == 1) {
            return runCatching {
                output.parentFile?.mkdirs()
                inputs[0].copyTo(output, overwrite = true)
                true
            }.getOrDefault(false)
        }

        var muxer: MediaMuxer? = null
        var started = false
        return runCatching {
            output.parentFile?.mkdirs()
            if (output.exists()) output.delete()

            var videoTrack = -1
            var timeOffsetUs = 0L

            for (file in inputs) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    val trackIndex = selectVideoTrack(extractor)
                    if (trackIndex < 0) continue
                    extractor.selectTrack(trackIndex)
                    val format = extractor.getTrackFormat(trackIndex)

                    if (muxer == null) {
                        muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                        videoTrack = muxer.addTrack(format)
                        muxer.start()
                        started = true
                    }

                    val buffer = ByteBuffer.allocate(256 * 1024)
                    val info = android.media.MediaCodec.BufferInfo()
                    var lastPtsUs = 0L
                    while (true) {
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        info.presentationTimeUs = extractor.sampleTime + timeOffsetUs
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(videoTrack, buffer, info)
                        lastPtsUs = extractor.sampleTime
                        extractor.advance()
                    }
                    timeOffsetUs += lastPtsUs + 33_000L // ~1 frame gap between segments
                } finally {
                    extractor.release()
                }
            }

            if (!started) {
                return false
            }
            output.exists() && output.length() > 0L
        }.getOrElse {
            println("LOG[ShockClip][MERGE] failed: ${it.message}")
            output.delete()
            false
        }.also {
            runCatching {
                if (started) muxer?.stop()
            }
            runCatching { muxer?.release() }
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i
        }
        return -1
    }
}
