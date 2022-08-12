# How to build

(a) Use IntelliJ IDEA, open project a click Build / Run.
    This also creates a `gradlew` script that can be used to run
    the builds from command line using:
    ```
    ./gradlew build
    ./gradlew run
    ```
(b) Use gradle:
    ```
    gradle build
    gradle run
    ```

Note that we are deviating from the common practice of commiting the generated
`gradlew` script and its accompanying JAR file, `gradle/wrapper/gradle-wrapper.jar`,
into git, because, well, that's ugly. And it makes merges more painful.
