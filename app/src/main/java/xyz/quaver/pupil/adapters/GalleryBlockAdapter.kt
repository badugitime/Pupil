/*
 *     Pupil, Hitomi.la viewer for Android
 *     Copyright (C) 2019  tom5079
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.quaver.pupil.adapters

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.interfaces.SwipeAdapterInterface
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.item_galleryblock.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.quaver.hitomi.getReader
import xyz.quaver.pupil.BuildConfig
import xyz.quaver.pupil.R
import xyz.quaver.pupil.favorites
import xyz.quaver.pupil.types.Tag
import xyz.quaver.pupil.util.Preferences
import xyz.quaver.pupil.util.downloader.Cache
import xyz.quaver.pupil.util.downloader.DownloadFolderManager
import xyz.quaver.pupil.util.wordCapitalize
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule

class GalleryBlockAdapter(private val glide: RequestManager, private val galleries: List<Int>) : RecyclerSwipeAdapter<RecyclerView.ViewHolder>(), SwipeAdapterInterface {

    enum class ViewType {
        NEXT,
        GALLERY,
        PREV
    }

    val timer = Timer()

    var isThin = false

    inner class GalleryViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        var timerTask: TimerTask? = null

        private fun updateProgress(context: Context, galleryID: Int) {
            val cache = Cache.getInstance(context, galleryID)

            CoroutineScope(Dispatchers.Main).launch {
                if (cache.metadata.reader == null || Preferences["cache_disable"]) {
                    view.galleryblock_progressbar.visibility = View.GONE
                    view.galleryblock_progress_complete.visibility = View.GONE
                    return@launch
                }

                with(view.galleryblock_progressbar) {
                    val imageList = cache.metadata.imageList!!

                    progress = imageList.filterNotNull().size

                    if (visibility == View.GONE) {
                        visibility = View.VISIBLE
                        max = imageList.size
                    }

                    if (progress == max) {
                        if (completeFlag.get(galleryID, false)) {
                            with(view.galleryblock_progress_complete) {
                                setImageResource(R.drawable.ic_progressbar)
                                visibility = View.VISIBLE
                            }
                        } else {
                            with(view.galleryblock_progress_complete) {
                                setImageDrawable(AnimatedVectorDrawableCompat.create(context, R.drawable.ic_progressbar_complete).apply {
                                    this?.start()
                                })
                                visibility = View.VISIBLE
                            }
                            completeFlag.put(galleryID, true)
                        }
                    } else
                        view.galleryblock_progress_complete.visibility = View.INVISIBLE
                }
            }
        }

        fun bind(galleryID: Int) {
            val cache = Cache.getInstance(view.context, galleryID)

            val galleryBlock = cache.metadata.galleryBlock!!

            with(view) {
                val resources = context.resources
                val languages = resources.getStringArray(R.array.languages).map {
                    it.split("|").let { split ->
                        Pair(split[0], split[1])
                    }
                }.toMap()

                val artists = galleryBlock.artists
                val series = galleryBlock.series

                if (isThin)
                    galleryblock_thumbnail.layoutParams.width = context.resources.getDimensionPixelSize(
                        R.dimen.galleryblock_thumbnail_thin
                    )
                galleryblock_thumbnail.setImageDrawable(CircularProgressDrawable(context).also {
                    it.start()
                })

                CoroutineScope(Dispatchers.IO).launch {
                    val thumbnail = cache.getThumbnail()

                    galleryblock_thumbnail.post {
                        glide
                            .load(thumbnail)
                            .skipMemoryCache(true)
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .error(R.drawable.image_broken_variant)
                            .apply {
                                if (BuildConfig.CENSOR)
                                    override(5, 8)
                            }
                            .into(galleryblock_thumbnail)
                    }
                }

                if (timerTask == null)
                    timerTask = timer.schedule(0, 1000) {
                        updateProgress(context, galleryID)
                    }

                galleryblock_title.text = galleryBlock.title
                with(galleryblock_artist) {
                    text = artists.joinToString(", ") { it.wordCapitalize() }
                    visibility = when {
                        artists.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }
                with(galleryblock_series) {
                    text =
                        resources.getString(
                            R.string.galleryblock_series,
                            series.joinToString(", ") { it.wordCapitalize() })
                    visibility = when {
                        series.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }
                galleryblock_type.text = resources.getString(R.string.galleryblock_type, galleryBlock.type).wordCapitalize()
                with(galleryblock_language) {
                    text =
                        resources.getString(R.string.galleryblock_language, languages[galleryBlock.language])
                    visibility = when {
                        galleryBlock.language.isNotEmpty() -> View.VISIBLE
                        else -> View.GONE
                    }
                }

                galleryblock_tag_group.removeAllViews()
                galleryBlock.relatedTags.forEach {
                    galleryblock_tag_group.addView(Chip(context).apply {
                        val tag = Tag.parse(it).let {  tag ->
                            when {
                                tag.area != null -> tag
                                else -> Tag("tag", it)
                            }
                        }

                        chipIcon = when(tag.area) {
                            "male" -> {
                                setChipBackgroundColorResource(R.color.material_blue_700)
                                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                ContextCompat.getDrawable(context, R.drawable.gender_male)?.apply {
                                    colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                                }
                            }
                            "female" -> {
                                setChipBackgroundColorResource(R.color.material_pink_600)
                                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                ContextCompat.getDrawable(context, R.drawable.gender_female)?.apply {
                                    colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                                }
                            }
                            else -> null
                        }
                        text = tag.tag.wordCapitalize()
                        setEnsureMinTouchTargetSize(false)
                        setOnClickListener {
                            for (callback in onChipClickedHandler)
                                callback.invoke(tag)
                        }
                    })
                }

                galleryblock_id.text = galleryBlock.id.toString()
                galleryblock_pagecount.text = "-"
                CoroutineScope(Dispatchers.IO).launch {
                    val pageCount = kotlin.runCatching {
                        getReader(galleryBlock.id).galleryInfo.files.size
                    }.getOrNull() ?: return@launch
                    withContext(Dispatchers.Main) {
                        galleryblock_pagecount.text = context.getString(R.string.galleryblock_pagecount, pageCount)
                    }
                }

                with(galleryblock_favorite) {
                    setImageResource(if (favorites.contains(galleryBlock.id)) R.drawable.ic_star_filled else R.drawable.ic_star_empty)
                    setOnClickListener {
                        when {
                            favorites.contains(galleryBlock.id) -> {
                                favorites.remove(galleryBlock.id)

                                setImageResource(R.drawable.ic_star_empty)
                            }
                            else -> {
                                favorites.add(galleryBlock.id)

                                setImageDrawable(AnimatedVectorDrawableCompat.create(context, R.drawable.avd_star).apply {
                                    this ?: return@apply

                                    registerAnimationCallback(object: Animatable2Compat.AnimationCallback() {
                                        override fun onAnimationEnd(drawable: Drawable?) {
                                            setImageResource(R.drawable.ic_star_filled)
                                        }
                                    })
                                    start()
                                })
                            }
                        }
                    }
                }


                // Make some views invisible to make it thinner
                if (isThin) {
                    galleryblock_language.visibility = View.GONE
                    galleryblock_type.visibility = View.GONE
                    galleryblock_tag_group.visibility = View.GONE
                }
            }
        }
    }
    class NextViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)
    class PrevViewHolder(view: LinearLayout) : RecyclerView.ViewHolder(view)

    class ViewHolderFactory {
        companion object {
            fun getLayoutID(type: Int): Int {
                return when(ViewType.values()[type]) {
                    ViewType.NEXT -> R.layout.item_next
                    ViewType.PREV -> R.layout.item_prev
                    ViewType.GALLERY -> R.layout.item_galleryblock
                }
            }
        }
    }

    val completeFlag = SparseBooleanArray()

    val onChipClickedHandler = ArrayList<((Tag) -> Unit)>()
    var onDownloadClickedHandler: ((Int) -> Unit)? = null
    var onDeleteClickedHandler: ((Int) -> Unit)? = null

    var showNext = false
    var showPrev = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        fun getViewHolder(type: Int, view: View): RecyclerView.ViewHolder {
            return when(ViewType.values()[type]) {
                ViewType.NEXT -> NextViewHolder(view as LinearLayout)
                ViewType.PREV -> PrevViewHolder(view as LinearLayout)
                ViewType.GALLERY -> GalleryViewHolder(view as CardView)
            }
        }

        return getViewHolder(
            viewType,
            LayoutInflater.from(parent.context).inflate(
                ViewHolderFactory.getLayoutID(viewType),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GalleryViewHolder) {
            val galleryID = galleries[position-(if (showPrev) 1 else 0)]

            holder.bind(galleryID)

            with(holder.view.galleryblock_primary) {
                setOnClickListener {
                    holder.view.performClick()
                }
                setOnLongClickListener {
                    holder.view.performLongClick()
                }
            }

            holder.view.galleryblock_download.setOnClickListener {
                onDownloadClickedHandler?.invoke(position)
            }

            holder.view.galleryblock_delete.setOnClickListener {
                onDeleteClickedHandler?.invoke(position)
            }

            mItemManger.bindView(holder.view, position)

            holder.view.galleryblock_swipe_layout.addSwipeListener(object: SwipeLayout.SwipeListener {
                override fun onStartOpen(layout: SwipeLayout?) {
                    mItemManger.closeAllExcept(layout)

                    holder.view.galleryblock_download.text =
                        if (DownloadFolderManager.getInstance(holder.view.context).isDownloading(galleryID))
                            holder.view.context.getString(android.R.string.cancel)
                        else
                            holder.view.context.getString(R.string.main_download)
                }

                override fun onClose(layout: SwipeLayout?) {}
                override fun onHandRelease(layout: SwipeLayout?, xvel: Float, yvel: Float) {}
                override fun onOpen(layout: SwipeLayout?) {}
                override fun onStartClose(layout: SwipeLayout?) {}
                override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {}
            })
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)

        if (holder is GalleryViewHolder) {
            holder.timerTask?.cancel()
            holder.timerTask = null
        }
    }

    override fun getItemCount() =
        galleries.size +
        (if (showNext) 1 else 0) +
        (if (showPrev) 1 else 0)

    override fun getItemViewType(position: Int): Int {
        return when {
            showPrev && position == 0 -> ViewType.PREV
            showNext && position == galleries.size+(if (showPrev) 1 else 0) -> ViewType.NEXT
            else -> ViewType.GALLERY
        }.ordinal
    }

    override fun getSwipeLayoutResourceId(position: Int) = R.id.galleryblock_swipe_layout
}