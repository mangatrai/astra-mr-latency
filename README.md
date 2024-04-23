# astra-mr-latency
This app can be used to verify approximate latencies to replicate data between two (or more) regions of a single multi-region Astra DB. Latencies are reported at consistency levels EACH_QUORUM and LOCAL_QUORUM.

The app performs below sets of operations in parallel (async) to compute replication latecies
- Inserts a sample amount of records (default 1000) in the primary region & captures start-time
- Reads all the inserted records from primary region along with timestamp
- Reads all the inserted records from secondary region along with timestamp

Note: Actual replication latency will be usually a bit less then the one reported by the app, as there will be some delay between when the data appears in a region to when the app reads it. Also note that these are replication latencies between regions and not latency between the Client (your app) and Server (Astra DB).

You can find more details about Astra's multi-region capabilities [here](https://www.datastax.com/blog/enhanced-multi-region-database-consistency-astra-db)
![Astra Multi-Region Consistency](./mr-consistency.png?raw=true)

## Prerequisite
- **Java11** (minimum) or higher
- **Maven 3.9.x** (minimum) or higher

## Building Jar 
1. Clone this repo
2. Move to the repo folder `cd astra-mr-latency`
3. Run the build `mvn clean package -Passembly`
4. The fat jar (`astra-mr-latency-*-jar-with-dependencies.jar`) file should now be present in the `target` folder

## Running the App
- Get the path details of secure-connect-bundle (SCB) for the two Astra regions
- Provide the Astra Client_ID for the Multi-Region (MR) DB
- Provide the Astra SECRET for the MR DB
- Run command `java -jar target/astra-mr-latency-1.0.1-jar-with-dependencies.jar "path-to-scb-region-1" "path-to-scb-region-2" "client-id" "client-secret"`
	- Optionally you can also pass the `number-of-records (default is 1000)` to insert. Note: Do not pass a value over 1K if this is not an enterprise DB that allows higher OPS/rate-limits.

## Sample Output
```
12:12:24.333 INFO  com.bhatman.astra.App    : =============== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: EACH_QUORUM ===============
12:12:27.187 INFO  com.bhatman.astra.AppUtil: us-east-1: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:12:31.058 INFO  com.bhatman.astra.AppUtil: us-west-2: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:12:31.404 INFO  com.bhatman.astra.App    : Test Started at: 1713888751404
12:12:31.588 INFO  com.bhatman.astra.App    : us-west-2: Found 134 rows!
12:12:31.653 INFO  com.bhatman.astra.App    : us-east-1: Found 1000 rows!
12:12:31.653 INFO  com.bhatman.astra.App    : us-east-1: Test completed in: 249ms
12:12:31.694 INFO  com.bhatman.astra.App    : us-west-2: Found 664 rows!
12:12:31.792 INFO  com.bhatman.astra.App    : us-west-2: Found 1000 rows!
12:12:31.792 INFO  com.bhatman.astra.App    : us-west-2: Test completed in: 388ms
12:12:31.805 INFO  com.bhatman.astra.App    : ===== 1000 row test at ConsistencyLevel EACH_QUORUM took 398ms. Avg. latency between regions was 65ms =====
12:12:35.928 INFO  com.bhatman.astra.App    : =============== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: LOCAL_QUORUM ===============
12:12:37.585 INFO  com.bhatman.astra.AppUtil: us-east-1: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:12:41.339 INFO  com.bhatman.astra.AppUtil: us-west-2: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:12:41.689 INFO  com.bhatman.astra.App    : Test Started at: 1713888761689
12:12:41.791 INFO  com.bhatman.astra.App    : us-east-1: Found 1000 rows!
12:12:41.791 INFO  com.bhatman.astra.App    : us-east-1: Test completed in: 102ms
12:12:41.805 INFO  com.bhatman.astra.App    : us-west-2: Found 220 rows!
12:12:41.900 INFO  com.bhatman.astra.App    : us-west-2: Found 1000 rows!
12:12:41.901 INFO  com.bhatman.astra.App    : us-west-2: Test completed in: 212ms
12:12:41.906 INFO  com.bhatman.astra.App    : ===== 1000 row test at ConsistencyLevel LOCAL_QUORUM took 216ms. Avg. latency between regions was 88ms =====
```
