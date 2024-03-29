# Tevis
Tevis brings the power of Apache Spark to your laptops to do data analysis with datasets either on your local machine or in a cluster.
Refer to the documentation here: https://github.com/SingTel-DataCo/Tevis/wiki

<img width="1489" alt="Screenshot 2023-08-04 at 12 29 01 AM" src="https://github.com/SingTel-DataCo/Tevis/assets/46181126/4fbbe749-6809-4b69-944a-48d37faf0242">

Using the common ANSI SQL, users can query, filter, join tables among other features, and build complex queries and utilize Spark SQL functions.

Tevis is also an end-to-end pipeline visualization tool for CAPEX.

<img width="1498" alt="Screenshot 2023-05-10 at 10 01 20 AM" src="https://github.com/SingTel-DataCo/Tevis/assets/46181126/a544e64b-705a-43a5-8b8c-c38238b32f17">

## Dataset Browser Features
1. Load CSV/Parquet files and auto-assign table names
   - Large files with hundreds of columns
   - Columns with very complex schema (e.g. map of list-map pairs)
2. Query tables using SQL
   - Use [Spark SQL built-in functions](https://spark.apache.org/docs/latest/api/sql/index.html)
   - Join different tables
3. Copy query results to clipboard
4. Download query results to CSV
5. Visualize query results with charts/maps
6. Add new query sections and tabs - use it like you would on Jupyter/Zeppelin notebooks
7. Save your workspace so tabs and query sections are persisted
8. Unmount datasets

## Requirements
1. Java 8
   > [!WARNING]
   > Java 11 or later will not work due to Scala 2.11 constraints.

For Windows users, there is no need to install **winutils.exe**

## Install and run Tevis locally

1. Download zip file from the [latest release](https://github.com/SingTel-DataCo/Tevis/releases) and unzip it.
2. Double-click on `run-app.bat` (on Windows) or `run-app.sh` (on Mac/Linux). This will run the application and automatically open your browser at `http://localhost:8085/`
3. Default credentials are `dataspark-admin` / `dataspark-admin`.

## Install and run Tevis on a YARN cluster

1. Download tar.gz file and copy it to the edge node of the cluster.
2. Rename `config/application-cluster.properties` to `config/application.properties`, and update with the correct and necessary Spark/Hadoop paths and configs.
3. Remove spark and hadoop libraries inside the application `lib` directory to avoid conflict with the cluster's spark and hadoop libraries.
4. Run `run-app.sh`. This will run the application at port `8085`.
5. Ensure that your application is accessible outside, otherwise use SSH tunneling.
   - For SSH tunneling: `ssh -L localhost:8085:localhost:8085 <edge-node-ip>`
4. On your browser, go to `http://<edge-node-ip>:8085/`
5. Default credentials are `dataspark-admin` / `dataspark-admin`.

## Limitations
> [!WARNING]
> 1. Locally-installed Tevis can only access local data, not remotely-stored data in S3, HDFS or via FTP.
> 2. Tevis configured to utilize spark cluster can access S3/HDFS datasets but not local datasets co-located to the machine where it is installed.
> 3. This version is not guaranteed to work with Spark 3.0 environment.

## Technologies used
1. Spring Boot
2. Scala 2.11
3. Spark 2.4.7
4. Apache Sedona 1.2.1
5. Java 8
6. Bootstrap 5
7. JQuery
8. Google Charts
9. Leaflet JS

## Troubleshooting
### 1. System memory size issue
If you encounter an error similar to the screenshot below:

<img width="453" alt="System memory size error" src="https://github.com/SingTel-DataCo/Tevis/assets/46181126/309b03c1-d03d-4298-ad7b-c7bd5e3eb0e7">

Try to update your `run-app.bat` or `run-app.sh` by inserting `-Xms500m` into your `java` command line call like so:
`java -Xms500m -Dapp.name="Tevis" ...`. This will set the JVM minimum heap space to 500 MB. If it still doesn't work, try with `-Xmx500m` which restricts the program from going beyond 500 MB heap-space usage, assuming that your computer has low RAM available.

### 2. Unsupported class version
If you encounter an error saying "Unsupported class file major version 55", this means that your Java version isn't 1.8. You will need to install Java 8 for the program to work.

---

If you have any questions, feel free to reach out to julius.delfino@dsanalytics.com.
