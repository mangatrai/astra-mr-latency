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
- Run command `java -jar target/astra-mr-latency-0.0.1-SNAPSHOT-jar-with-dependencies.jar`
