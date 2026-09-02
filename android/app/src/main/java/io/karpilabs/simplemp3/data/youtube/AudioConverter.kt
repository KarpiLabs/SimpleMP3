package io.karpilabs.simplemp3.data.youtube

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Thin wrapper around bundled FFmpegKit — mirrors yt-dl's:
 *   yt-dlp -x --audio-format mp3 --audio-quality 0 --embed-thumbnail --embed-metadata
 *
 * FFmpeg itself is LGPL-licensed; this is a format transform, not a legal shield
 * for downloading restricted content. Prefer keeping source audio when possible
 * for efficiency; MP3 re-encode costs CPU/battery and is lossy-on-lossy.
 */
object AudioConverter {
    data class Metadata(
        val title: String,
        val artist: String,
        val album: String = "YouTube",
    )

    /**
     * Convert any ffmpeg-readable audio file to MP3 (libmp3lame, VBR quality 0),
     * optionally embedding a JPEG/PNG cover and ID3 title/artist/album.
     *
     * @return the [outputMp3] on success
     */
    fun convertToMp3(
        input: File,
        outputMp3: File,
        metadata: Metadata,
        coverImage: File? = null,
    ): Result<File> =
        convertToMp3Internal(
            input = input,
            outputMp3 = outputMp3,
            metadata = metadata,
            coverImage = coverImage,
            audioArgs = listOf("-c:a", "libmp3lame", "-q:a", "0"),
        )

    /**
     * Storage-oriented re-encode at a fixed bitrate (for large movie-length files).
     */
    fun convertToMp3AtBitrate(
        input: File,
        outputMp3: File,
        metadata: Metadata,
        bitrateKbps: Int,
        coverImage: File? = null,
    ): Result<File> =
        convertToMp3Internal(
            input = input,
            outputMp3 = outputMp3,
            metadata = metadata,
            coverImage = coverImage,
            audioArgs = listOf("-c:a", "libmp3lame", "-b:a", "${bitrateKbps.coerceIn(64, 320)}k"),
        )

    /**
     * Save a network stream (progressive URL or HLS `.m3u8`) to a local `.m4a` by
     * stream-copying the audio — no re-encode, so it's fast and lossless. Requires
     * an FFmpeg build with network protocol (https/tls) support; HLS segments are
     * typically ADTS AAC, so [aac_adtstoasc] is applied for a valid MP4/M4A.
     *
     * @param input a remote URL or a local file path FFmpeg can read
     * @return the [outputM4a] on success
     */
    fun remuxToM4a(
        input: String,
        outputM4a: File,
        metadata: Metadata,
    ): Result<File> {
        outputM4a.parentFile?.mkdirs()
        outputM4a.delete()

        // Use argument array with executeWithArguments to prevent argument injection / shell breakout
        val args =
            arrayOf(
                "-y",
                "-i", input,
                "-map", "0:a:0",
                "-c:a", "copy",
                "-bsf:a", "aac_adtstoasc",
                "-movflags", "+faststart",
                "-id3v2_version", "3",
                "-metadata", "title=${metadata.title}",
                "-metadata", "artist=${metadata.artist}",
                "-metadata", "album=${metadata.album}",
                outputM4a.absolutePath,
            )

        val session = FFmpegKit.executeWithArguments(args)
        val code = session.returnCode
        return when {
            ReturnCode.isSuccess(code) && outputM4a.exists() && outputM4a.length() > 0L ->
                Result.success(outputM4a)
            ReturnCode.isCancel(code) ->
                Result.failure(IllegalStateException("Save cancelled"))
            else -> {
                outputM4a.delete()
                val fail =
                    session.failStackTrace?.takeIf { it.isNotBlank() }
                        ?: session.output?.takeLast(400)
                        ?: "FFmpeg failed (code=${code?.value})"
                Result.failure(IllegalStateException(fail))
            }
        }
    }

    private fun convertToMp3Internal(
        input: File,
        outputMp3: File,
        metadata: Metadata,
        coverImage: File?,
        audioArgs: List<String>,
    ): Result<File> {
        if (!input.exists() || input.length() == 0L) {
            return Result.failure(IllegalStateException("Input audio missing or empty"))
        }
        outputMp3.parentFile?.mkdirs()
        outputMp3.delete()

        fun buildArgs(withCover: Boolean): Array<String> {
            val args = mutableListOf("-y", "-i", input.absolutePath)
            if (withCover && coverImage != null && coverImage.exists() && coverImage.length() > 0L) {
                args.addAll(
                    listOf(
                        "-i", coverImage.absolutePath,
                        "-map", "0:a",
                        "-map", "1:0",
                        "-c:v", "copy",
                        "-disposition:v:0", "attached_pic",
                        "-metadata:s:v", "title=Album cover",
                        "-metadata:s:v", "comment=Cover (front)",
                    ),
                )
            } else {
                args.addAll(listOf("-map", "0:a"))
            }
            args.addAll(audioArgs)
            args.addAll(
                listOf(
                    "-id3v2_version", "3",
                    "-metadata", "title=${metadata.title}",
                    "-metadata", "artist=${metadata.artist}",
                    "-metadata", "album=${metadata.album}",
                    "-metadata", "album_artist=${metadata.artist}",
                    outputMp3.absolutePath,
                ),
            )
            return args.toTypedArray()
        }

        val session = FFmpegKit.executeWithArguments(buildArgs(withCover = true))
        val code = session.returnCode
        return when {
            ReturnCode.isSuccess(code) && outputMp3.exists() && outputMp3.length() > 0L ->
                Result.success(outputMp3)
            ReturnCode.isCancel(code) ->
                Result.failure(IllegalStateException("Conversion cancelled"))
            else -> {
                if (coverImage != null) {
                    outputMp3.delete()
                    val retry = FFmpegKit.executeWithArguments(buildArgs(withCover = false))
                    if (ReturnCode.isSuccess(retry.returnCode) &&
                        outputMp3.exists() &&
                        outputMp3.length() > 0L
                    ) {
                        Result.success(outputMp3)
                    } else {
                        val fail =
                            retry.failStackTrace?.takeIf { it.isNotBlank() }
                                ?: retry.output?.takeLast(400)
                                ?: "FFmpeg failed (code=${retry.returnCode?.value})"
                        Result.failure(IllegalStateException(fail))
                    }
                } else {
                    val fail =
                        session.failStackTrace?.takeIf { it.isNotBlank() }
                            ?: session.output?.takeLast(400)
                            ?: "FFmpeg failed (code=${code?.value})"
                    Result.failure(IllegalStateException(fail))
                }
            }
        }
    }

}
