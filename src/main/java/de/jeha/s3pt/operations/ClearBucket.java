package de.jeha.s3pt.operations;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import de.jeha.s3pt.OperationResult;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.*;
import java.util.*;


/**
 * @author jenshadlich@googlemail.com
 */
public class ClearBucket extends AbstractOperation {

    private static final Logger LOG = LoggerFactory.getLogger(ClearBucket.class);

    private final AmazonS3 s3Client;
    private final String bucket;
    private final int n;
    private final int threads;

    public class DeleteObject extends AbstractOperation {
        private final AmazonS3 s3client;
        private final String bucket;
        private final String key;
        private final OperationResult operationResult;

        public DeleteObject(AmazonS3 s3client, String bucket, String key, OperationResult operationResult) {
            this.s3client = s3client;
            this.bucket = bucket;
            this.key = key;
            this.operationResult = operationResult;
        }

        public OperationResult call() throws Exception {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            s3client.deleteObject(bucket, key);

            stopWatch.stop();

            LOG.debug("Time = {} ms", stopWatch.getTime());
            operationResult.getStats().addValue(stopWatch.getTime());
            return operationResult;
        }
    }

    public ClearBucket(AmazonS3 s3Client, String bucket, int n, int threads) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.n = n;
        this.threads = threads;
    }

    @Override
    public OperationResult call() throws Exception {
        LOG.info("Clear bucket: bucket={}, n={}", bucket, n);
        if (threads > 1) {
            return callParallel();
        }

        int deleted = 0;
        boolean truncated;
        do {
            ObjectListing objectListing = s3Client.listObjects(bucket);
            truncated = objectListing.isTruncated();

            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                LOG.debug("Delete object: {}, #deleted {}", objectSummary.getKey(), deleted);

                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                s3Client.deleteObject(bucket, objectSummary.getKey());

                stopWatch.stop();

                LOG.debug("Time = {} ms", stopWatch.getTime());
                getStats().addValue(stopWatch.getTime());

                deleted++;
                if (deleted >= n) {
                    break;
                }
                if (deleted % 1000 == 0) {
                    LOG.info("Objects deleted so far: {}", deleted);
                }
            }
        } while (truncated && deleted < n);

        LOG.info("Object deleted: {}", deleted);

        return new OperationResult(getStats());
    }

    public OperationResult callParallel() throws Exception {
        OperationResult oResult = new OperationResult(getStats());
        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        List<Callable<OperationResult>> operations = new ArrayList<>();

        int scheduled = 0;
        boolean truncated;
        do {
            ObjectListing objectListing = s3Client.listObjects(bucket);
            truncated = objectListing.isTruncated();

            for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
                LOG.info("Delete object: {}, #scheduled {}", objectSummary.getKey(), scheduled);

                operations.add(new DeleteObject(s3Client, bucket, objectSummary.getKey(), oResult));

                scheduled++;
                if (scheduled >= n) {
                    break;
                }
                if (scheduled % 1000 == 0) {
                    LOG.info("Objects scheduled so far: {}", scheduled);
                }
            }
        } while (truncated && scheduled < n);

        LOG.info("Object scheduled: {}", scheduled);

        try {
            List<Future<OperationResult>> futureResults = executorService.invokeAll(operations);
            int deleted = 0;
            List<OperationResult> operationResults = new ArrayList<>();
            for (Future<OperationResult> result : futureResults) {
                result.get();
                deleted++;
                LOG.info("Deleted object: {}", deleted);
            }

        } catch (InterruptedException | ExecutionException e) {
            LOG.error("An error occurred", e);
        }

        executorService.shutdown();
        return new OperationResult(getStats());

    }
}
