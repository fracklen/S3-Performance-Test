package de.jeha.s3pt.operations;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import de.jeha.s3pt.OperationResult;
import de.jeha.s3pt.operations.util.RandomDataGenerator;
import de.jeha.s3pt.utils.RandomGeneratedInputStream;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;
import java.math.BigInteger;

/**
 * @author jenshadlich@googlemail.com
 */
public class UploadAndRead extends AbstractOperation {

    private final static Logger LOG = LoggerFactory.getLogger(UploadAndRead.class);

    private final AmazonS3 s3Client;
    private final String bucket;
    private final int n;
    private final int size;

    public UploadAndRead(AmazonS3 s3Client, String bucket, int n, int size) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.n = n;
        this.size = size;
    }

    @Override
    public OperationResult call() {
        LOG.info("Upload: n={}, size={} byte", n, size);

        for (int i = 0; i < n; i++) {
            //final byte data[] = RandomDataGenerator.generate(size);
            final String key = UUID.randomUUID().toString();
            LOG.debug("Uploading object: {}", key);

            final ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(size);
            RandomGeneratedInputStream uploadStream = new RandomGeneratedInputStream(size);
            PutObjectRequest putObjectRequest =
                    new PutObjectRequest(bucket, key, uploadStream, objectMetadata);


            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            s3Client.putObject(putObjectRequest);

            S3Object object = s3Client.getObject(bucket, key);



            try {
                InputStream downloadStream = object.getObjectContent();
                String downloadHash = calc(downloadStream);
                if (downloadHash == "") {
                    LOG.info("Inconsistent upload-read");
                }
                object.close();
            } catch (IOException e) {
                LOG.warn("An exception occurred while trying to close object with key: {}", key);
            }

            stopWatch.stop();

            LOG.debug("Time = {} ms", stopWatch.getTime());
            getStats().addValue(stopWatch.getTime());

            if (i > 0 && i % 1000 == 0) {
                LOG.info("Progress: {} of {}", i, n);
            }
        }

        return new OperationResult(getStats());
    }

    public static String calc(InputStream is) {
        String output;
        int read;
        byte[] buffer = new byte[4096];

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            BigInteger bigInt = new BigInteger(1, hash);
            output = bigInt.toString(16);
            while ( output.length() < 32 ) {
                output = "0"+output;
            }
        }
        catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }

        return output;
    }
}
