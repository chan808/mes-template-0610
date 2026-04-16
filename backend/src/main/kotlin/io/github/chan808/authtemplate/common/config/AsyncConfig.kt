package io.github.chan808.authtemplate.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("mailTaskExecutor")
    fun mailTaskExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 8
            queueCapacity = 200
            setThreadNamePrefix("mail-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(10)
            initialize()
        }
}
