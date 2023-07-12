package br.com.shido.photogrid.ui.components

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive


@Composable
fun PhotoGrid() {
    val photos by rememberSaveable {
        mutableStateOf(List(100) { Photo(id = it, url = randomSampleImageUrl()) })
    }

    val selectedIds = rememberSaveable {
        mutableStateOf(emptySet<Int>())
    }

    val inSelectionMode by remember { derivedStateOf { selectedIds.value.isNotEmpty() } }

    val state = rememberLazyGridState()

    val autoScrollSpeed = remember { mutableStateOf(0f) }

    /**
     * Whenever the value of the scroll speed variable changes, the LaunchedEffect is retriggered and the scrolling will restart.
     *
     * You might wonder why we didn’t directly change the scroll level from within the onDrag handler. The reason is that the onDrag lambda is only called when the user actually moves the pointer! So if the user holds their finger very still on the screen, the scrolling would stop. You might have noticed this scrolling bug in apps before, where you need to “scrub” the bottom of your screen to let it scroll.
     *
     *
     */
    LaunchedEffect(autoScrollSpeed.value) {
        if (autoScrollSpeed.value != 0f) {
            while (isActive) {
                state.scrollBy(autoScrollSpeed.value)
                delay(10)
            }
        }
    }

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(minSize = 128.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = {
            items(photos, key = { it.id }) { photo ->
                val selected = selectedIds.value.contains(photo.id)

                ImageItem(selected = selected,
                    inSelectionMode = inSelectionMode, photo = photo, modifier = Modifier
                        .photoGridDragHandler(
                            lazyGridState = state,
                            selectedIds = selectedIds,
                            autoScrollSpeed = autoScrollSpeed,
                            autoScrollThreshold = with(LocalDensity.current) { 40.dp.toPx() }
                        )
                        .semantics {
                            if (!inSelectionMode) {
                                onLongClick("Select") {
                                    selectedIds.value += photo.id
                                    true
                                }
                            }
                        }
                        .then(
                            if (inSelectionMode) {
                                Modifier.toggleable(value = selected,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onValueChange = {
                                        if (it) {
                                            selectedIds.value += photo.id
                                        } else {
                                            selectedIds.value -= photo.id
                                        }
                                    })
                            } else {
                                Modifier
                            }
                        )
                )
            }
        })
}

fun Modifier.photoGridDragHandler(
    lazyGridState: LazyGridState,
    selectedIds: MutableState<Set<Int>>,
    autoScrollSpeed: MutableState<Float>,
    autoScrollThreshold: Float
) = pointerInput(Unit) {
    var initialKey: Int? = null
    var currentKey: Int? = null
    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->

            /**
             * Finds the key of the item underneath the pointer, if any. This represents the element that the user is long-pressing and will start the drag gesture from.
             * If it finds an item (the user is pointing at an element in the grid), it checks if this item is still unselected (thereby fulfilling requirement 4).
             * Sets both the initial and the current key to this key value, and proactively adds it to the list of selected elements.
             *
             */
            lazyGridState.gridItemKeyAtPosition(offset)?.let { key ->
                if (selectedIds.value.contains(key).not()) {
                    initialKey = key
                    currentKey = key
                    selectedIds.value = selectedIds.value + key
                }
            }

        },
        onDragCancel = {
            initialKey = null
            autoScrollSpeed.value = 0f
        },
        onDragEnd = {
            initialKey = null
            autoScrollSpeed.value = 0f
        },
        onDrag = { change, dragAmount ->
            if (initialKey != null) {
                // If dragging near the vertical edges of the grid, start scrolling
                val distFromBottom =
                    lazyGridState.layoutInfo.viewportSize.height - change.position.y
                val distFromTop = change.position.y

                /**
                 * As you can see, we update the scroll speed based on the threshold and distance, and make sure to reset the scroll speed when the drag ends or is canceled.
                 *
                 * Now changing this scroll speed value from the gesture handler doesn’t do anything yet. We need to update the PhotoGrid composable to start scrolling the grid when the value changes:
                 */

                autoScrollSpeed.value = when {
                    distFromBottom < autoScrollThreshold -> autoScrollThreshold - distFromBottom
                    distFromBottom < autoScrollThreshold -> (autoScrollThreshold - distFromTop)
                    else -> 0f
                }

                // Add or remove photos from selection based on drag position
                /**
                 * A drag is only handled when the initial key is set. Based on the initial key and the current key,
                 * this lambda will update the set of selected items. It makes sure that all elements between the initial
                 * key and the current key are selected.
                 */
                lazyGridState.gridItemKeyAtPosition(change.position)?.let { key ->
                    if (currentKey != key) {
                        selectedIds.value = selectedIds.value.minus(initialKey!!..currentKey!!)
                            .minus(currentKey!!..initialKey!!)
                            .plus(initialKey!!..key)
                            .plus(key..initialKey!!)
                        currentKey = key
                    }
                }
            }


        })
}

/**
 * For each visible item in the grid, this method checks if the hitPoint falls within its bounds.
 *
 */
fun LazyGridState.gridItemKeyAtPosition(hitPoint: Offset): Int? =
    layoutInfo.visibleItemsInfo.find { itemInfo ->
        itemInfo.size.toIntRect().contains(hitPoint.round() - itemInfo.offset)
    }?.key as? Int

@Composable
fun ImageItem(
    photo: Photo,
    modifier: Modifier = Modifier, selected: Boolean, inSelectionMode: Boolean,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = modifier.aspectRatio(1f)
    ) {
        Box {
            val transition = updateTransition(selected, label = "selected")
            val padding by transition.animateDp(label = "padding") { selected ->
                if (selected) 10.dp else 0.dp
            }
            val roundedCornerShape by transition.animateDp(label = "corner") { selected ->
                if (selected) 16.dp else 0.dp
            }

            Image(
                painter = rememberAsyncImagePainter(photo.url),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .padding(padding)
                    .clip(RoundedCornerShape(roundedCornerShape.value))
            )
            if (inSelectionMode) {
                if (selected) {
                    val bgColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                    Icon(
                        Icons.Filled.CheckCircle,
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(4.dp)
                            .border(2.dp, bgColor, CircleShape)
                            .clip(CircleShape)
                            .background(bgColor)
                    )
                } else {
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        tint = Color.White.copy(alpha = 0.7f),
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }

    }
}

class Photo(val id: Int, val url: String)


fun randomSampleImageUrl() = "https://picsum.photos/seed/${(0..100000).random()}/256/256"
