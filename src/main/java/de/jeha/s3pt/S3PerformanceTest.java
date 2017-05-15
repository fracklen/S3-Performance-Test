package de.jeha.s3pt;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import de.jeha.s3pt.operations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

import java.util.concurrent.*;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;


import com.google.gson.Gson;

/**
 * @author jenshadlich@googlemail.com
 */
public class S3PerformanceTest implements Callable<TestResult> {

    private static final Logger LOG = LoggerFactory.getLogger(S3PerformanceTest.class);

    private final String accessKey;
    private final String secretKey;
    private final String endpointUrl;
    private final String bucketName;
    private final Operation operation;
    private final int threads;
    private final int n;
    private final int size;
    private final boolean useHttp;
    private final boolean useGzip;
    private final String signerOverride;
    private final boolean useKeepAlive;
    private final boolean usePathStyleAccess;
    private final String keyFileName;
    private final String kairosdbUrl;
    private final String source;
    private final String backend;
    private final List<KairosdbPoint> pointBuffer;

    /**
     * @param accessKey      access key
     * @param secretKey      secret key
     * @param endpointUrl    endpoint url, e.g. 's3.amazonaws.com'
     * @param bucketName     name of bucket
     * @param operation      operation
     * @param threads        number of threads
     * @param n              number of operations
     * @param size           size (if applicable), e.g. for UPLOAD operation
     * @param useHttp        switch to HTTP when
     * @param useGzip        enable GZIP compression
     * @param signerOverride override the S3 signer
     * @param useKeepAlive   use TCP keep alive
     * @param keyFileName    name of file with object keys
     * @param kairosdbUrl    Url of KairosDB for posting metrics
     * @param source         source tag for metrics
     * @param backend        backend tag for metrics
     */
    public S3PerformanceTest(String accessKey, String secretKey, String endpointUrl, String bucketName,
                             Operation operation, int threads, int n, int size, boolean useHttp, boolean useGzip,
                             String signerOverride, boolean useKeepAlive, boolean usePathStyleAccess,
                             String keyFileName,
                             String kairosdbUrl, String source, String backend) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.endpointUrl = endpointUrl;
        this.bucketName = bucketName;
        this.operation = operation;
        this.threads = threads;
        this.n = n;
        this.size = size;
        this.useHttp = useHttp;
        this.useGzip = useGzip;
        this.signerOverride = signerOverride;
        this.useKeepAlive = useKeepAlive;
        this.usePathStyleAccess = usePathStyleAccess;
        this.keyFileName = keyFileName;
        this.kairosdbUrl = kairosdbUrl;
        this.source = source;
        this.backend = backend;
        this.pointBuffer = new ArrayList<KairosdbPoint>();
    }

    @Override
    public TestResult call() {
        AmazonS3 s3Client = buildS3Client();

        ExecutorService executorService = Executors.newFixedThreadPool(threads);

        List<Callable<OperationResult>> operations = new ArrayList<>();
        if (operation.isMultiThreaded()) {
            for (int i = 0; i < threads; i++) {
                operations.add(createOperation(operation, s3Client));
            }
        } else {
            if (threads > 1) {
                LOG.warn("operation {} does not support multiple threads, use single thread", operation);
            }
            operations.add(createOperation(operation, s3Client));
        }

        TestResult testResult = null;
        try {
            List<Future<OperationResult>> futureResults = executorService.invokeAll(operations);

            List<OperationResult> operationResults = new ArrayList<>();
            for (Future<OperationResult> result : futureResults) {
                OperationResult res = result.get();
                operationResults.add(res);
            }

            testResult = TestResult.compute(operationResults);
            pushResults(testResult);

        } catch (InterruptedException | ExecutionException e) {
            LOG.error("An error occurred", e);
        }

        executorService.shutdown();

        LOG.info("Done");

        return testResult;
    }

    // -----------------------------------------------------------------------------------------------------------------

    /**
     * @return S3 client
     */
    private AmazonS3 buildS3Client() {
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);

        ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withProtocol(useHttp ? Protocol.HTTP : Protocol.HTTPS)
                .withUserAgent("s3pt")
                .withGzip(useGzip)
                .withTcpKeepAlive(useKeepAlive);

        if (signerOverride != null) {
            String signer = signerOverride.endsWith("Type")
                    ? signerOverride
                    : signerOverride + "Type";
            clientConfiguration.setSignerOverride(signer);
        }

        AmazonS3 s3Client = new AmazonS3Client(credentials, clientConfiguration);
        s3Client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(usePathStyleAccess).disableChunkedEncoding().build());
        s3Client.setEndpoint(endpointUrl);

        return s3Client;
    }

    /**
     * Build the given operation.
     *
     * @param operation operation (enum)
     * @param s3Client  S3 client
     * @return operation
     */
    private AbstractOperation createOperation(Operation operation, AmazonS3 s3Client) {
        switch (operation) {
            case CLEAR_BUCKET:
                return new ClearBucket(s3Client, bucketName, n, threads);
            case CREATE_BUCKET:
                return new CreateBucket(s3Client, bucketName);
            case DELETE_BUCKET:
                return new DeleteBucket(s3Client, bucketName);
            case CREATE_KEY_FILE:
                return new CreateKeyFile(s3Client, bucketName, n, keyFileName);
            case RANDOM_READ:
                return new RandomRead(s3Client, bucketName, n, keyFileName);
            case RANDOM_READ_METADATA:
                return new RandomReadMetadata(s3Client, bucketName, n, keyFileName);
            case UPLOAD:
                return new Upload(s3Client, bucketName, n, size);
            case UPLOAD_AND_READ:
                return new UploadAndRead(s3Client, bucketName, n, size);
            default:
                throw new UnsupportedOperationException("Unknown operation: " + operation);
        }
    }

    private void pushResults(TestResult testResult) {
        int i = 0;
        List<KairosdbPoint> points = new ArrayList<KairosdbPoint>();

        Map<String, String> tags = new HashMap<String, String>();
        tags.put("backend", "riak");
        tags.put("op", operation.toString());
        tags.put("size", operation.toString() + String.valueOf(size));
        tags.put("source", source);
        tags.put("backend", backend);
        tags.put("threads", "T" + String.valueOf(threads));

        pushPoint(new KairosdbPoint("core.engineering.s3.operation_time.avg",
            System.currentTimeMillis(),
            testResult.getAvg(),
            tags));
        pushPoint(new KairosdbPoint("core.engineering.s3.operation_time.min",
            System.currentTimeMillis(),
            testResult.getMin(),
            tags));
        pushPoint(new KairosdbPoint("core.engineering.s3.operation_time.max",
            System.currentTimeMillis(),
            testResult.getMax(),
            tags));
        pushPoint(new KairosdbPoint("core.engineering.s3.operation_time.p99",
            System.currentTimeMillis(),
            testResult.getP99(),
            tags));
        pushPoint(new KairosdbPoint("core.engineering.s3.operation_time.p95",
            System.currentTimeMillis(),
            testResult.getP95(),
            tags));

        pushPoint(new KairosdbPoint("core.engineering.s3.ops",
            System.currentTimeMillis() + (i++),
            testResult.getOps(),
            tags));
        pushPoint(null);
    }

    private void pushPoint(KairosdbPoint point) {
        if (point != null) {
            pointBuffer.add(point);
            if (pointBuffer.size() < 100) {
                return;
            }
        }
        try {
            String       postUrl       = kairosdbUrl + "/api/v1/datapoints";// put in your url
            Gson         gson          = new Gson();
            HttpClient   httpClient    = HttpClientBuilder.create().build();
            HttpPost     post          = new HttpPost(postUrl);
            StringEntity postingString = new StringEntity(gson.toJson(pointBuffer));//gson.tojson() converts your pojo to json
            post.setEntity(postingString);
            post.setHeader("Content-type", "application/json");
            HttpResponse  response = httpClient.execute(post);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
            //handle exception here

        } finally {
            pointBuffer.clear();
            //Deprecated
            //httpClient.getConnectionManager().shutdown();
        }
    }

}
