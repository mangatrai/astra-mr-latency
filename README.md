# astra-mr-latency
This app will report the below two latencies for Multi-Region (MR) Apps deployed on Astra
- Latency between the two regions
- Latencies for CL EACH_QUORUM and LOCAL_QUORUM

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
