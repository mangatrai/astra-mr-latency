# astra-mr-latency
App to help check latencies between AstraDB regions (for Multi-Region (MR) Apps deployed on Astra)

## Prerequisite
- **Java11** (minimum) or higher
- **Maven 3.9.x** (minimum) or higher

## Building Jar 
1. Clone this repo
2. Move to the repo folder `cd astra-mr-latency`
3. Run the build `mvn clean package -Passembly`
4. The fat jar (`astra-mr-latency-*-jar-with-dependencies.jar`) file should now be present in the `target` folder

## Running the App
- Add secure-connect-bundle (SCB) details for the two Astra regions
- Provide the Astra Client_ID for the Multi-Region (MR) DB
- Provide the Astra SECRET for the MR DB
- Run command `java -jar target/astra-mr-latency-1.0.1-jar-with-dependencies.jar`

## Sample Output
```
11:21:08.297 INFO  com.bhatman.astra.App    : ====================== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: EACH_QUORUM ======================
11:21:10.606 INFO  com.bhatman.astra.App    : us-east-1: Connected!
11:21:11.119 INFO  com.bhatman.astra.AppUtil: us-east-1: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
11:21:14.685 INFO  com.bhatman.astra.App    : us-west-2: Connected!
11:21:15.065 INFO  com.bhatman.astra.AppUtil: us-west-2: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
11:21:15.518 INFO  com.bhatman.astra.App    : Test Started at: 1711552875518
11:21:15.610 INFO  com.bhatman.astra.App    : us-east-1: Looking for rows...
11:21:15.611 INFO  com.bhatman.astra.App    : us-west-2: Looking for rows...
11:21:15.719 INFO  com.bhatman.astra.App    : us-west-2: Found 265 rows!
11:21:15.719 INFO  com.bhatman.astra.App    : us-west-2: Looking for rows...
11:21:15.727 INFO  com.bhatman.astra.App    : us-east-1: Found 666 rows!
11:21:15.728 INFO  com.bhatman.astra.App    : us-east-1: Looking for rows...
11:21:15.814 INFO  com.bhatman.astra.App    : us-west-2: Found 516 rows!
11:21:15.815 INFO  com.bhatman.astra.App    : us-west-2: Looking for rows...
11:21:15.834 INFO  com.bhatman.astra.App    : us-east-1: Found 1000 rows!
11:21:15.834 INFO  com.bhatman.astra.App    : us-east-1: Test completed in: 316ms
11:21:15.915 INFO  com.bhatman.astra.App    : us-west-2: Found 1000 rows!
11:21:15.915 INFO  com.bhatman.astra.App    : us-west-2: Test completed in: 397ms
11:21:18.034 INFO  com.bhatman.astra.AppUtil: us-east-1: Closed connection!
11:21:20.109 INFO  com.bhatman.astra.AppUtil: us-west-2: Closed connection!
11:21:20.109 INFO  com.bhatman.astra.App    : =================== Total Latency: 77993, AVG Latency: 77, Rowcount: 1000, ConsistencyLevel: EACH_QUORUM ===========================
11:21:20.110 INFO  com.bhatman.astra.App    : ====================== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: LOCAL_QUORUM ======================
11:21:21.206 INFO  com.bhatman.astra.App    : us-east-1: Connected!
11:21:21.759 INFO  com.bhatman.astra.AppUtil: us-east-1: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
11:21:25.182 INFO  com.bhatman.astra.App    : us-west-2: Connected!
11:21:25.556 INFO  com.bhatman.astra.AppUtil: us-west-2: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
11:21:25.927 INFO  com.bhatman.astra.App    : Test Started at: 1711552885927
11:21:25.963 INFO  com.bhatman.astra.App    : us-east-1: Looking for rows...
11:21:25.963 INFO  com.bhatman.astra.App    : us-west-2: Looking for rows...
11:21:26.051 INFO  com.bhatman.astra.App    : us-east-1: Found 1000 rows!
11:21:26.051 INFO  com.bhatman.astra.App    : us-east-1: Test completed in: 124ms
11:21:26.069 INFO  com.bhatman.astra.App    : us-west-2: Found 171 rows!
11:21:26.069 INFO  com.bhatman.astra.App    : us-west-2: Looking for rows...
11:21:26.169 INFO  com.bhatman.astra.App    : us-west-2: Found 1000 rows!
11:21:26.169 INFO  com.bhatman.astra.App    : us-west-2: Test completed in: 242ms
11:21:28.332 INFO  com.bhatman.astra.AppUtil: us-east-1: Closed connection!
11:21:30.382 INFO  com.bhatman.astra.AppUtil: us-west-2: Closed connection!
11:21:30.382 INFO  com.bhatman.astra.App    : =================== Total Latency: 101653, AVG Latency: 101, Rowcount: 1000, ConsistencyLevel: LOCAL_QUORUM ===========================
```

