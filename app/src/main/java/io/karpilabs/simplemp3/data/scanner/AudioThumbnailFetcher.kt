package io.karpilabs.simplemp3.data.scanner

import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * The legacy `content://media/external/audio/albumart/{id}` table is deprecated since
 * scoped storage (API 29) and often fails to resolve through a regular app's
 * ContentResolver even though privileged system loaders (e.g. the media notification)
 * can still read it. `ContentResolver.loadThumbnail` is the API 29+ replacement and works
 * uniformly for audio/video/image content URIs, so route MediaStore audio URIs through it
 * instead of letting Coil try to decode them as a generic image stream.
 */
class AudioThumbnailFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = runCatching {
            context.contentResolver.loadThumbnail(uri, THUMBNAIL_SIZE, null)
        }.getOrNull() ?: return null

        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (data.authority != "media" || !data.pathSegments.contains("audio")) return null
            return AudioThumbnailFetcher(context, data)
        }
    }

    private companion object {
        val THUMBNAIL_SIZE = Size(512, 512)
    }
}
