package com.zula.features.media

import org.jobrunr.scheduling.BackgroundJob
import org.jobrunr.scheduling.cron.Cron
import org.slf4j.LoggerFactory

/**
 * JobRunr entrypoints for media object garbage collection.
 * Scheduled daily from [com.zula.core.jobrunr.configureJobRunr] after JobRunr init.
 */
class MediaGcJobs(
    private val mediaService: MediaService,
) {
    fun runDailyGc() {
        val n = mediaService.gcOnce()
        log.info("Media GC removed {} object(s)", n)
    }

    companion object {
        private val log = LoggerFactory.getLogger(MediaGcJobs::class.java)
        const val RECURRING_JOB_ID = "media-gc-daily"

        /**
         * Registers a daily recurring GC job via JobRunr IoC (KoinJobActivator).
         * Call only after JobRunr.initialize().
         */
        fun scheduleRecurring() {
            BackgroundJob.scheduleRecurrently<MediaGcJobs>(
                RECURRING_JOB_ID,
                Cron.daily(),
            ) { jobs -> jobs.runDailyGc() }
            log.info("Scheduled recurring media GC ({})", RECURRING_JOB_ID)
        }
    }
}
