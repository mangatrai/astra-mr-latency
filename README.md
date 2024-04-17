# astra-mr-latency
This app will report the below two latencies for Multi-Region (MR) Apps deployed on Astra
- Latency between the two regions
- Latencies for CL EACH_QUORUM and LOCAL_QUORUM

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
12:53:56.784 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: =============== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: EACH_QUORUM ===============
12:53:59.298 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Connected!
12:53:59.832 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-east-1: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:54:03.343 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Connected!
12:54:03.716 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-west-2: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:54:04.059 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: Test Started at: 1713372844058
12:54:04.142 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Looking for rows...
12:54:04.143 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Looking for rows...
12:54:04.242 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Found 207 rows!
12:54:04.242 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Looking for rows...
12:54:04.300 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Found 594 rows!
12:54:04.300 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Looking for rows...
12:54:04.333 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Found 352 rows!
12:54:04.333 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Looking for rows...
12:54:04.372 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Found 1000 rows!
12:54:04.372 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Test completed in: 314ms
12:54:04.438 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Found 1000 rows!
12:54:04.438 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Test completed in: 380ms
12:54:04.443 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: ===== 1000 row test at ConsistencyLevel EACH_QUORUM took 383ms. Avg. latency between regions was 53ms =====
12:54:06.528 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-east-1: Closed connection!
12:54:08.600 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-west-2: Closed connection!
12:54:08.600 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: =============== PERFORMING MULTI-REGION LATENCY CHEACK WITH CONSISTENCY-LEVEL: LOCAL_QUORUM ===============
12:54:09.536 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Connected!
12:54:11.317 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-east-1: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:54:14.476 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Connected!
12:54:14.939 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-west-2: Table 'LATENCY_CHECK' has been created (if not exists) OR truncated (if exists).
12:54:15.275 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: Test Started at: 1713372855275
12:54:15.307 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Looking for rows...
12:54:15.307 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Looking for rows...
12:54:15.352 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Found 1000 rows!
12:54:15.352 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-east-1: Test completed in: 77ms
12:54:15.398 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Found 266 rows!
12:54:15.398 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Looking for rows...
12:54:15.515 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Found 1000 rows!
12:54:15.515 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: us-west-2: Test completed in: 240ms
12:54:15.517 [35mINFO [0;39m [36mcom.bhatman.astra.App    [0;39m: ===== 1000 row test at ConsistencyLevel LOCAL_QUORUM took 242ms. Avg. latency between regions was 133ms =====
12:54:17.575 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-east-1: Closed connection!
12:54:19.628 [35mINFO [0;39m [36mcom.bhatman.astra.AppUtil[0;39m: us-west-2: Closed connection!
```

