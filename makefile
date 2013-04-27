
JAR=sdfs/target/sdfs.jar

run: $(JAR)
	java -jar $(JAR)

$(JAR):
	java -jar sbt-launch.jar clean sdfs/assembly

jar: $(JAR)

.PHONY: run jar

