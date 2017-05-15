RIAK_ACCESS_KEY ?= RIAK_CS_ACCESS_KEY_ABCD
RIAK_SECRET_KEY ?= c3d2a7d77cdfb816c8f54210d11f768e
RIAK_ENDPOINT   ?= http://riak-cs.example.net:8080
CEPH_ACCESS_KEY ?= CEPH_ACCESS_KEY_ABCDEFG
CEPH_SECRET_KEY ?= c3d2a7d77cdfb816c8f54210d11f768e
CEPH_ENDPOINT   ?= http://rados-gw.example.net:7480
KAIROSDB_URL    ?= http://kairosdb.example.net:8000

BUCKET ?= test-bucket
FNUM ?= 100
THREADS ?= 1
OPERATION ?= UPLOAD

ifeq ($(BACKEND),CEPH)
$(info Using Ceph)
ACCESS_KEY := $(CEPH_ACCESS_KEY)
SECRET_KEY := $(CEPH_SECRET_KEY)
ENDPOINT   := $(CEPH_ENDPOINT)
endif
ifeq ($(BACKEND), RIAK)
$(info Using riak)
ACCESS_KEY := $(RIAK_ACCESS_KEY)
SECRET_KEY := $(RIAK_SECRET_KEY)
ENDPOINT   := $(RIAK_ENDPOINT)
endif

foo:
	echo $(ACCESS_KEY)


build:
	mvn clean install

stats:
	for i in $$(seq 1 100); do $(MAKE) run_suite; done
	echo $(MAKE) clear_bucket

run_suite: test-2048 test-1M test-256 test-1024M clear_bucket

delete_bucket:
	java -jar target/s3pt.jar \
	  --endpointUrl $(ENDPOINT) \
	  --accessKey   $(ACCESS_KEY) \
	  --secretKey   $(SECRET_KEY) \
	  --bucketName  $(BUCKET) \
	  --number      1 \
	  --threads     1 \
	  --keepAlive \
	  --operation   DELETE_BUCKET \
	  --kairosdbUrl $(KAIROSDB_URL)

clear_bucket:
	java -jar target/s3pt.jar \
	  --endpointUrl $(ENDPOINT) \
	  --accessKey   $(ACCESS_KEY) \
	  --secretKey   $(SECRET_KEY) \
	  --bucketName  $(BUCKET) \
	  --number      $(FNUM) \
	  --threads     $(THREADS) \
	  --keepAlive \
	  --operation   CLEAR_BUCKET \
	  --kairosdbUrl $(KAIROSDB_URL)

test-%:
	java -jar target/s3pt.jar \
	  --endpointUrl $(ENDPOINT) \
	  --accessKey   $(ACCESS_KEY) \
	  --secretKey   $(SECRET_KEY) \
	  --bucketName  $(BUCKET) \
	  --number      $(FNUM) \
	  --threads     $(THREADS) \
	  --keepAlive \
	  --size        $* \
	  --kairosdbUrl $(KAIROSDB_URL)

testop-%:
	java -jar target/s3pt.jar \
	  --endpointUrl $(ENDPOINT) \
	  --accessKey   $(ACCESS_KEY) \
	  --secretKey   $(SECRET_KEY) \
	  --bucketName  $(BUCKET) \
	  --number      $(FNUM) \
	  --operation   $(OPERATION) \
	  --threads     $(THREADS) \
	  --keepAlive \
	  --size        $* \
	  --kairosdbUrl $(KAIROSDB_URL)
