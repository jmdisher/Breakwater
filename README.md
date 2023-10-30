# Breakwater

Thin wrapper over Jetty to streamline common REST or WebSocket use-cases.

## How to build

Breakwater is a Maven project so can be built and tested using the top-level command:

```
mvn clean install
```

## Maven coordinates

Breakwater can be accessed as a Maven dependency by adding this repository information:

```
	<repositories>
		<repository>
			<id>breakwater-repo</id>
			<url>https://github.com/jmdisher/Breakwater/raw/maven/repo</url>
		</repository>
	</repositories>
```

From there, the `0.3.1` release (for example - check the tag list for updated releases:  https://github.com/jmdisher/Breakwater/tags), can be included:

```
		<dependency>
			<groupId>com.jeffdisher.breakwater</groupId>
			<artifactId>rest-server</artifactId>
			<version>0.3.1</version>
		</dependency>
```

