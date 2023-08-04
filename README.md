# Tevis
Use Tevis to do data analysis with datasets either on your local machine or in a cluster.
<img width="1489" alt="Screenshot 2023-08-04 at 12 29 01 AM" src="https://github.com/SingTel-DataCo/Tevis/assets/46181126/4fbbe749-6809-4b69-944a-48d37faf0242">

Apache Spark is a powerful tool to read large datasets, and Tevis takes advantage on this library and makes it easy for
end-users to leverage on it for analytics. Using the common ANSI SQL, users can query, filter, join tables among other features, and build complex queries and utilize Spark SQL functions.

Tevis is also an end-to-end pipeline visualization tool for CAPEX.
<img width="1498" alt="Screenshot 2023-05-10 at 10 01 20 AM" src="https://github.com/SingTel-DataCo/Tevis/assets/46181126/a544e64b-705a-43a5-8b8c-c38238b32f17">

Refer to the documentation here: https://github.com/SingTel-DataCo/Tevis/wiki

## Dataset Browser Features
1. Load CSV/Parquet files and auto-assign table names
   - Large files with 100s of columns
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

For Windows users, there is no need to install winutils.exe

## Install and run Tevis locally
### On Windows

1. Download zip file from the [latest release](https://github.com/SingTel-DataCo/Tevis/releases) and unzip it.
2. Double-click on `run-app.bat`. This will run the application and automatically open your browser at `http://localhost:8080/`
3. Default credentials are dataspark-admin/dataspark-admin.

### On Mac/Linux

1. Download zip file from the [latest release](https://github.com/SingTel-DataCo/Tevis/releases) and unzip it.
2. Run Terminal app and go to your Tevis root directory.
2. Run `run-app.sh`. This will run the application and automatically open your browser at `http://localhost:8080/`
3. Default credentials are `dataspark-admin` / `dataspark-admin`.

## Install and run Tevis on a YARN cluster

1. Download tar.gz file and copy it to the edge node of the cluster.
2. Rename `config/application-cluster.properties` to `config/application.properties`, and update with the correct and necessary Spark/Hadoop paths and configs.
3. Run `run-app.sh`. This will run the application at port `8080`.
4. Ensure that your application is accessible outside, otherwise use SSH tunneling.
   - For SSH tunneling: `ssh -L localhost:8080:localhost:8080 <edge-node-ip>`
4. On your browser, go to `http://<edge-node-ip>:8080/`
5. Default credentials are `dataspark-admin` / `dataspark-admin`.

## Use cases to load datasets on Tevis
1. Load a single CSV or Parquet file:
   - On the "Dataset Browser" page, click on "New Dataset" and specify the path of the file.
   - Once added, you will see a new entry in the navigation pane. Expand that entry to see the single table.
   - Click on the table -> "Read dataset" to load initial SQL and data.
2. Load a folder containing different CSV or Parquet files
   - On the "Dataset Browser" page, click on "New Dataset" and specify the folder path.
   - Once added, you will see a new entry in the navigation pane. Expand that entry to see all the tables, each corresponding to the file.
   - Select a table to read -> "Read dataset" to load initial SQL and data.
3. Load a folder obtained from HDFS/S3 with several files (normally this folder contains a file called "_SUCCESS")
   - On the "Dataset Browser" page, click on "New Dataset" and specify the folder path with _SUCCESS file.
   - Once added, you will see a new entry in the navigation pane. Expand that entry to see the single table.
   - Click on the table -> "Read dataset" to load initial SQL and data.
4. Load a root folder containing a mix of subfolders as stated in #3
   - On the "Dataset Browser" page, click on "New Dataset" and specify the root folder path.
   - Once added, you will see a new entry in the navigation pane. Expand that entry to see all the tables, each corresponding to the subfolders.
   - Select a table to read -> "Read dataset" to load initial SQL and data.

## Limitations
1. Locally-installed Tevis can only access local data, not remotely-stored data in S3, HDFS or via FTP.
2. Tevis configured to utilize spark cluster can access S3/HDFS datasets but not local datasets co-located to the machine where it is installed.

## Technologies used
1. Spring Boot
2. Scala 2.11
3. Java 8
4. Bootstrap 5
5. JQuery

If you have any questions, feel free to reach out to julius.delfino@dsanalytics.com.
