package com.gathin.tvhome;

import java.util.concurrent.*;
/** Process-wide serial image worker. Cancelled owners remove queued tasks; no bitmap task backlog. */
final class CinemaImageDecoder {
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"cinema-image");t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    static {WORKER.allowCoreThreadTimeOut(true);}
    static ExecutorService worker(){return WORKER;}
    static void cancel(Future<?> task){if(task!=null){task.cancel(false);if(task instanceof Runnable)WORKER.remove((Runnable)task);}}
    static int queued(){return WORKER.getQueue().size();}
    private CinemaImageDecoder(){}
}
