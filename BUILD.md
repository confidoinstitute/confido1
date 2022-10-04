# How to build

(a) Use IntelliJ IDEA, open project a click Build / Run.
    This also creates a `gradlew` script that can be used to run
    the builds from command line using:
    ```
    ./gradlew build
    ```
(b) Use gradle:
    ```
    gradle build
    ```
Note that we are deviating from the common practice of commiting the generated
`gradlew` script and its accompanying JAR file, `gradle/wrapper/gradle-wrapper.jar`,
into git, because, well, that's ugly. And it makes merges more painful.

# Running

To run use the gradle `run` task (either via IDE Run button or `./gradlew run`). This
runs the backend, which serves compiled frontend from `build/distributions/confido1.js`
(built during the build step.) This can be overriden by setting `CONFIDO_STATIC_PATH`
to a different directory.

After running, the app listens on `http://localhost:8080/`

## Database

The backend needs MongoDB to run. It should suffice to run a local MongoDB daemon,
it will create a database automatically. To fill it with some example questions,
send a GET request to `http://localhost:8080/init`.

## Frontend hot reload

To support hot reload, use the `jsBrowserDevelopmentRun --continuous` gradle task.
You can either configure this as a new run configuration in the IDE or run
`./gradlew jsBrowserDevelopmentRun` from a terminal.

This runs a webpack development server at `http://localhost:8081/`, which serves
the frontend files. Backend requests are proxied to `http://localhost:8080/` so
you have to also separately run the backend as shown above. IDEA allows running
two run configurations at the same time, or you can run two gradle commands in two
terminal windows.
