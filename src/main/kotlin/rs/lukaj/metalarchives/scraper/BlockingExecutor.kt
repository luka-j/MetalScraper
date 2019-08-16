package rs.lukaj.metalarchives.scraper

import java.util.concurrent.BlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit


class BlockingExecutor(private val name: String = "" ,private val concurrentTasksLimit: Int,
                       corePool: Int, maxPool: Int, keepAliveSec: Long, queue: BlockingQueue<Runnable>)
    : ThreadPoolExecutor(corePool, maxPool, keepAliveSec, TimeUnit.SECONDS, queue) {

    private var semaphore = Semaphore(concurrentTasksLimit)

    override fun execute(command: Runnable) {
        if(concurrentTasksLimit == Integer.MAX_VALUE) {super.execute(command); return}

        var waitTime=0
        while(!semaphore.tryAcquire(2, TimeUnit.MINUTES)) {
            waitTime+=2
            System.err.println("Warning: executor $name waiting for semaphore $waitTime minutes; possible starvation.")
        }

        val wrapped = {
            try {
                command.run()
            } finally {
                semaphore.release()
            }
        }

        if(!isShutdown) {
            super.execute(wrapped)
        } else {
            System.err.println("Warning: executor $name has shutdown; executing task on current thread")
            wrapped()
        }
    }

    fun await() {
        while(!queue.isEmpty()) {
            Thread.sleep(400)
        }
    }
}