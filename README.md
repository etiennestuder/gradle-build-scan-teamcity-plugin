gradle-build-scan-teamcity-plugin
=================================

# Overview

[Gradle](http://www.gradle.org) plugin for build scans that notifies [TeamCity](https://www.jetbrains.com/teamcity/) when 
a build scan is published during a build. The plugin works in collaboration with the [Build scan TeamCity integration](https://github.com/etiennestuder/teamcity-build-scan-plugin).

The TeamCity build scan plugin is hosted at [Bintray's JCenter](https://bintray.com/etienne/gradle-plugins/gradle-build-scan-teamcity-plugin).

# Goals

When not using TeamCity's GradleRunner to launch Gradle builds, this plugin can be used to notify TeamCity about the scans that were published while
running a build. If you use the GradleRunner to launch Gradle builds, there is no need to apply the TeamCity build scan plugin. 

# Functionality

The TeamCity build scan plugin sends a service message that contains the published build scan URLs to TeamCity via Gradle's logging infrastructure. The service 
message is only interpreted by TeamCity if the [Build scan TeamCity integration](https://github.com/etiennestuder/teamcity-build-scan-plugin) is enabled 
on the TeamCity server.

# Design

The service message sent via Gradle's logging infrastructure follows the message pattern expected by TeamCity which is `##teamcity[message-name 'arguments']`.

# Configuration

## Apply TeamCity build scan plugin

Apply the `nu.studer.build-scan.teamcity` plugin to your Gradle plugin project.

### Gradle 1.x and 2.0

```groovy
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'nu.studer:gradle-build-scan-teamcity-plugin:1.0'
    }
}

apply plugin: 'nu.studer.build-scan.teamcity'
```

### Gradle 2.1 and higher

```groovy
plugins {
  id 'nu.studer.build-scan.teamcity' version '1.0'
}
```

Please refer to the [Gradle DSL PluginDependenciesSpec](http://www.gradle.org/docs/current/dsl/org.gradle.plugin.use.PluginDependenciesSpec.html) to
understand the behavior and limitations when using the new syntax to declare plugin dependencies.

# Feedback and Contributions

Both feedback and contributions are very welcome.

# Acknowledgements

+ [mark-vieira](https://github.com/mark-vieira) (pr #6 that provides message service functionality)

# License

This plugin is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html).

(c) by Etienne Studer
