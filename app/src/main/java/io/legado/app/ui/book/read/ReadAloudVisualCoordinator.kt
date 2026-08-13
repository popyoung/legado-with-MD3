package io.legado.app.ui.book.read

import io.legado.app.service.ReadAloudPlaybackCursor

data class ReadAloudViewport(
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterPosition: Int,
    val renderChapterIndex: Int = chapterIndex,
    val renderPageIndex: Int = pageIndex,
    val effectiveOffset: Int = 0
)

internal data class ReadAloudVisualTarget(
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterPosition: Int,
    val pagePosition: Int,
    val paragraphNum: Int
)

enum class ReadAloudManualStepTrigger {
    Dialog,
    ReadViewAction
}

internal data class ReadAloudPresentation(
    val cursor: ReadAloudPlaybackCursor,
    val viewport: ReadAloudViewport?,
    val paragraphVisible: Boolean
)

internal class ReadAloudVisualCoordinator {

    enum class FollowState {
        Following,
        Detached,
        Restoring
    }

    enum class RestoreReason {
        Progress,
        BackNavigation,
        ResumePlayback,
        ForegroundReturn
    }

    enum class ManualStepSource {
        PlaybackCursor,
        VisualCenter
    }

    sealed interface RenderOrigin {
        data object User : RenderOrigin
        data object Programmatic : RenderOrigin
        data class Restore(val token: Long) : RenderOrigin
    }

    data class Restore(
        val token: Long,
        val target: ReadAloudPlaybackCursor,
        val reason: RestoreReason
    )

    data class State(
        val follow: FollowState = FollowState.Following,
        val playbackCursor: ReadAloudPlaybackCursor? = null,
        val restore: Restore? = null,
        val viewport: ReadAloudViewport? = null,
        val settledPresentation: ReadAloudPresentation? = null
    )

    sealed interface Event {
        data class PlaybackProgress(val presentation: ReadAloudPresentation) : Event

        data class UserScrollStarted(
            val cursor: ReadAloudPlaybackCursor,
            val viewport: ReadAloudViewport? = null
        ) : Event

        data class UserScrollSettled(val presentation: ReadAloudPresentation) : Event

        data class RestoreRequested(
            val cursor: ReadAloudPlaybackCursor,
            val reason: RestoreReason
        ) : Event

        data class RenderChanged(
            val origin: RenderOrigin,
            val viewport: ReadAloudViewport
        ) : Event

        data class RestoreApplied(
            val token: Long,
            val presentation: ReadAloudPresentation
        ) : Event

        data class RestoreTimedOut(val token: Long) : Event
        data class BackNavigationRequested(val presentation: ReadAloudPresentation) : Event

        data class ManualStepRequested(
            val presentation: ReadAloudPresentation,
            val visualPositionEnabled: Boolean,
            val visualTarget: ReadAloudVisualTarget?
        ) : Event

        data class ForegroundReturned(
            val presentation: ReadAloudPresentation,
            val playbackContinued: Boolean
        ) : Event

        data class PlaybackPaused(val cursor: ReadAloudPlaybackCursor) : Event
        data class PlaybackResumed(val presentation: ReadAloudPresentation) : Event
        data object PlaybackStopped : Event
    }

    sealed interface Effect {
        data object DetachViewport : Effect
        data object UpdateHighlight : Effect
        data object ClearHighlight : Effect
        data object ApplyLowerEdgeFollow : Effect
        data object ShowCenterIndicator : Effect
        data object HideCenterIndicator : Effect
        data object PausePlayback : Effect

        data class StartRestore(
            val token: Long,
            val target: ReadAloudPlaybackCursor,
            val reason: RestoreReason
        ) : Effect

        data class StepFrom(
            val source: ManualStepSource,
            val visualTarget: ReadAloudVisualTarget?
        ) : Effect
    }

    data class Transition(
        val state: State,
        val effects: List<Effect> = emptyList()
    )

    var state = State()
        private set

    private var nextRestoreToken = 1L

    fun reduce(event: Event): Transition = when (event) {
        is Event.PlaybackProgress -> onPlaybackProgress(event.presentation)
        is Event.UserScrollStarted -> onUserScrollStarted(event)
        is Event.UserScrollSettled -> onUserScrollSettled(event.presentation)
        is Event.RestoreRequested -> startRestore(event.cursor, event.reason)
        is Event.RenderChanged -> onRenderChanged(event)
        is Event.RestoreApplied -> onRestoreApplied(event)
        is Event.RestoreTimedOut -> onRestoreTimedOut(event.token)
        is Event.BackNavigationRequested -> onBackNavigation(event.presentation)
        is Event.ManualStepRequested -> onManualStep(event)
        is Event.ForegroundReturned -> onForegroundReturned(event)
        is Event.PlaybackPaused -> onPlaybackPaused(event.cursor)
        is Event.PlaybackResumed -> onPlaybackResumed(event.presentation)
        Event.PlaybackStopped -> update(
            State(),
            Effect.ClearHighlight,
            Effect.HideCenterIndicator
        )
    }

    private fun onPlaybackProgress(presentation: ReadAloudPresentation): Transition {
        val restore = state.restore
        if (restore != null) {
            if (presentation.paragraphVisible) {
                return update(
                    state.copy(
                        follow = FollowState.Following,
                        playbackCursor = presentation.cursor,
                        restore = null,
                        viewport = presentation.viewport,
                        settledPresentation = null
                    ),
                    Effect.UpdateHighlight,
                    Effect.ApplyLowerEdgeFollow,
                    Effect.HideCenterIndicator
                )
            }
            return startRestore(
                cursor = presentation.cursor,
                reason = restore.reason,
                viewport = presentation.viewport
            )
        }

        return when (state.follow) {
            FollowState.Following -> {
                if (presentation.paragraphVisible) {
                    update(
                        state.copy(
                            playbackCursor = presentation.cursor,
                            viewport = presentation.viewport,
                            settledPresentation = null
                        ),
                        Effect.UpdateHighlight,
                        Effect.ApplyLowerEdgeFollow,
                        Effect.HideCenterIndicator
                    )
                } else {
                    startRestore(presentation.cursor, RestoreReason.Progress, presentation.viewport)
                }
            }

            FollowState.Detached -> update(
                state.copy(
                    playbackCursor = presentation.cursor,
                    viewport = presentation.viewport,
                    settledPresentation = null
                ),
                *visiblePresentationEffects(presentation)
            )

            FollowState.Restoring -> error("Restoring state requires a restore request")
        }
    }

    private fun onUserScrollStarted(event: Event.UserScrollStarted): Transition {
        val alreadyDetached = state.follow == FollowState.Detached &&
                state.restore == null &&
                state.viewport == event.viewport
        val nextState = state.copy(
            follow = FollowState.Detached,
            playbackCursor = event.cursor,
            restore = null,
            viewport = event.viewport,
            settledPresentation = null
        )
        return if (alreadyDetached) update(nextState) else update(nextState, Effect.DetachViewport)
    }

    private fun onUserScrollSettled(presentation: ReadAloudPresentation): Transition {
        if (state.follow == FollowState.Detached && state.settledPresentation == presentation) {
            return update(state)
        }
        return update(
            state.copy(
                follow = FollowState.Detached,
                playbackCursor = presentation.cursor,
                restore = null,
                viewport = presentation.viewport,
                settledPresentation = presentation
            ),
            *visiblePresentationEffects(presentation)
        )
    }

    private fun onRenderChanged(event: Event.RenderChanged): Transition = when (val origin = event.origin) {
        RenderOrigin.User -> update(
            state.copy(
                follow = FollowState.Detached,
                restore = null,
                viewport = event.viewport,
                settledPresentation = null
            ),
            Effect.DetachViewport
        )

        RenderOrigin.Programmatic -> update(
            state.copy(viewport = event.viewport, settledPresentation = null)
        )

        is RenderOrigin.Restore -> {
            if (state.restore?.token != origin.token) {
                update(state)
            } else {
                update(state.copy(viewport = event.viewport, settledPresentation = null))
            }
        }
    }

    private fun onRestoreApplied(event: Event.RestoreApplied): Transition {
        val restore = state.restore
        if (restore?.token != event.token || restore.target != event.presentation.cursor) {
            return update(state)
        }
        if (!event.presentation.paragraphVisible) {
            return update(
                state.copy(viewport = event.presentation.viewport),
                Effect.StartRestore(restore.token, restore.target, restore.reason)
            )
        }
        return update(
            state.copy(
                follow = FollowState.Following,
                playbackCursor = event.presentation.cursor,
                restore = null,
                viewport = event.presentation.viewport,
                settledPresentation = null
            ),
            Effect.HideCenterIndicator
        )
    }

    private fun onRestoreTimedOut(token: Long): Transition {
        if (state.restore?.token != token) return update(state)
        return update(
            state.copy(
                follow = FollowState.Detached,
                restore = null,
                settledPresentation = null
            ),
            Effect.ShowCenterIndicator
        )
    }

    private fun onBackNavigation(presentation: ReadAloudPresentation): Transition {
        return if (presentation.paragraphVisible) {
            update(
                state.copy(
                    playbackCursor = presentation.cursor,
                    viewport = presentation.viewport
                ),
                Effect.PausePlayback
            )
        } else {
            startRestore(
                presentation.cursor,
                RestoreReason.BackNavigation,
                presentation.viewport
            )
        }
    }

    private fun onManualStep(event: Event.ManualStepRequested): Transition {
        val useVisualTarget = !event.presentation.paragraphVisible &&
                event.visualPositionEnabled &&
                event.visualTarget != null
        val source = if (useVisualTarget) {
            ManualStepSource.VisualCenter
        } else {
            ManualStepSource.PlaybackCursor
        }
        return update(
            state.copy(
                follow = FollowState.Following,
                playbackCursor = event.presentation.cursor,
                restore = null,
                viewport = event.presentation.viewport,
                settledPresentation = null
            ),
            Effect.HideCenterIndicator,
            Effect.StepFrom(
                source = source,
                visualTarget = event.visualTarget.takeIf { useVisualTarget }
            )
        )
    }

    private fun onForegroundReturned(event: Event.ForegroundReturned): Transition {
        return if (event.presentation.paragraphVisible) {
            update(
                state.copy(
                    follow = FollowState.Following,
                    playbackCursor = event.presentation.cursor,
                    restore = null,
                    viewport = event.presentation.viewport,
                    settledPresentation = null
                ),
                Effect.UpdateHighlight,
                Effect.ApplyLowerEdgeFollow,
                Effect.HideCenterIndicator
            )
        } else {
            startRestore(
                event.presentation.cursor,
                RestoreReason.ForegroundReturn,
                event.presentation.viewport
            )
        }
    }

    private fun onPlaybackPaused(cursor: ReadAloudPlaybackCursor): Transition = update(
        state.copy(
            follow = FollowState.Detached,
            playbackCursor = cursor,
            restore = null,
            settledPresentation = null
        ),
        Effect.ClearHighlight,
        Effect.HideCenterIndicator
    )

    private fun onPlaybackResumed(presentation: ReadAloudPresentation): Transition {
        return if (presentation.paragraphVisible) {
            update(
                state.copy(
                    follow = FollowState.Following,
                    playbackCursor = presentation.cursor,
                    restore = null,
                    viewport = presentation.viewport,
                    settledPresentation = null
                ),
                Effect.UpdateHighlight,
                Effect.ApplyLowerEdgeFollow,
                Effect.HideCenterIndicator
            )
        } else {
            startRestore(
                presentation.cursor,
                RestoreReason.ResumePlayback,
                presentation.viewport
            )
        }
    }

    private fun startRestore(
        cursor: ReadAloudPlaybackCursor,
        reason: RestoreReason,
        viewport: ReadAloudViewport? = state.viewport
    ): Transition {
        val restore = Restore(nextRestoreToken++, cursor, reason)
        return update(
            state.copy(
                follow = FollowState.Restoring,
                playbackCursor = cursor,
                restore = restore,
                viewport = viewport,
                settledPresentation = null
            ),
            Effect.StartRestore(restore.token, restore.target, restore.reason)
        )
    }

    private fun visiblePresentationEffects(
        presentation: ReadAloudPresentation
    ): Array<out Effect> = if (presentation.paragraphVisible) {
        arrayOf(Effect.UpdateHighlight, Effect.HideCenterIndicator)
    } else {
        arrayOf(Effect.ShowCenterIndicator)
    }

    private fun update(nextState: State, vararg effects: Effect): Transition {
        state = nextState
        return Transition(nextState, effects.toList())
    }
}
