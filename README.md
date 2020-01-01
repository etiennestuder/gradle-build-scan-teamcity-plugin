gradle-build-scan-teamcity-plugin
=================================

> The work on this software project is in no way associated with my employer nor with the role I'm having at my employer. Any requests for changes will be decided upon exclusively by myself based on my personal preferences. I maintain this project as much or as little as my spare time permits.

# Overview

[Gradle](http://www.gradle.org) plugin for [build scans](https://scans.gradle.com/) that notifies [TeamCity](https://www.jetbrains.com/teamcity/) when
a build scan is published during a build. The plugin works in collaboration with the [Build scan TeamCity integration](https://github.com/etiennestuder/teamcity-build-scan-plugin).

The TeamCity build scan Gradle plugin is hosted at [Bintray's JCenter](https://bintray.com/etienne/gradle-plugins/gradle-build-scan-teamcity-plugin).

# Goals

When not using TeamCity's GradleRunner to launch Gradle builds, this plugin can be used to notify TeamCity about the scans that were published while
running a build. If you use the GradleRunner to launch Gradle builds, there is no need to apply the TeamCity build scan Gradle plugin to your builds.

# Functionality

The TeamCity build scan Gradle plugin sends a service message that contains the published build scan URLs to TeamCity via Gradle's logging infrastructure. The service
message is only interpreted by TeamCity if the [Build scan TeamCity integration](https://github.com/etiennestuder/teamcity-build-scan-plugin) is enabled
on the TeamCity server.

# Design

The service message sent via Gradle's logging infrastructure follows the message pattern expected by TeamCity which is `##teamcity[message-name 'arguments']`.

# Configuration

## Apply TeamCity build scan Gradle plugin

### Project-application

For Gradle versions < 6.0, apply the `nu.studer.build-scan.teamcity` plugin (and the `com.gradle.build-scan` plugin) to your Gradle project.

```groovy
plugins {
  id 'nu.studer.build-scan.teamcity' version '1.1'
  id 'com.gradle.build-scan' version '3.1.1'
}
```

Please refer to the [Gradle DSL PluginDependenciesSpec](http://www.gradle.org/docs/current/dsl/org.gradle.plugin.use.PluginDependenciesSpec.html) to
understand the behavior and limitations when using the new syntax to declare plugin dependencies.

### Settings-application

For Gradle versions >= 6.0, apply the `nu.studer.build-scan.teamcity` plugin (and the `com.gradle.enterprise` plugin) to your Gradle settings file.

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath 'nu.studer:gradle-build-scan-teamcity-plugin:1.1'
    }
}

plugins {
  id 'com.gradle.enterprise' version '3.1.1'
}

apply plugin: 'nu.studer.build-scan.teamcity'
```

# Feedback and Contributions

Both feedback and contributions are very welcome.

# Acknowledgements

+ [facewindu](https://github.com/facewindu) (pr for Gradle Enterprise plugin integration)
+ [guylabs](https://github.com/guylabs) (pr for Gradle Enterprise plugin integration)
+ [mark-vieira](https://github.com/mark-vieira) (pr that provides message service functionality)

# License

This plugin is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

(c) by Etienne Studer
