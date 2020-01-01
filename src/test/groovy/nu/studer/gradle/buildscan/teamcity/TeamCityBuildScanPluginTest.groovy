package nu.studer.gradle.buildscan.teamcity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import org.gradle.util.GradleVersion
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import spock.lang.AutoCleanup

import java.util.zip.GZIPOutputStream

class TeamCityBuildScanPluginTest extends BaseFuncTest {

    static final String PUBLIC_BUILD_SCAN_ID = "i2wepy2gr7ovw"

    static final String BUILD_SCAN_PLUGIN_CLASSPATH_SYS_PROP = 'buildScanPluginClasspath'
    static final String GRADLE_ENTERPRISE_PLUGIN_CLASSPATH_SYS_PROP = 'gradleEnterprisePluginClasspath'

    @AutoCleanup
    def mockScansServer = GroovyEmbeddedApp.of {
        def objectMapper = new ObjectMapper(new SmileFactory())

        handlers {
            post("in/:gradleVersion/:pluginVersion") { ctx ->
                def scanUrlString = "${mockScansServer.address}s/" + PUBLIC_BUILD_SCAN_ID
                def os = new ByteArrayOutputStream()

                new GZIPOutputStream(os).withCloseable { stream ->
                    def generator = objectMapper.getFactory().createGenerator(stream)
                    generator.writeStartObject()
                    generator.writeFieldName("id")
                    generator.writeString(PUBLIC_BUILD_SCAN_ID)
                    generator.writeFieldName("scanUrl")
                    generator.writeString(scanUrlString)
                    generator.writeEndObject()
                    generator.close()
                }

                response.contentType("application/vnd.gradle.scan-ack").send(os.toByteArray())
            }
        }
    }

    def "service messages emitted when build scan plugin applied in project build file"() {
        given:
        gradleVersion = GradleVersion.version('5.6.4')
        addToTestKitRunnerPluginClasspath(BUILD_SCAN_PLUGIN_CLASSPATH_SYS_PROP)

        and:
        buildFile << """
            plugins {
                id 'com.gradle.build-scan' version '3.1.1'
                id 'nu.studer.build-scan.teamcity'
            }

            buildScan {
                server = '${mockScansServer.address}'
                publishAlways()
            }
"""

        when:
        def result = runWithArguments('tasks', '-S')

        then:
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_STARTED'")
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_SCAN_URL:${mockScansServer.address}s/${PUBLIC_BUILD_SCAN_ID}'")
    }

    def "service messages emitted when build scan plugin applied after TC gradle plugin in project build file"() {
        given:
        gradleVersion = GradleVersion.version('5.6.4')
        addToTestKitRunnerPluginClasspath(BUILD_SCAN_PLUGIN_CLASSPATH_SYS_PROP)

        and:
        buildFile << """
            plugins {
                id 'nu.studer.build-scan.teamcity'
                id 'com.gradle.build-scan' version '3.1.1'
            }

            buildScan {
                server = '${mockScansServer.address}'
                publishAlways()
            }
"""

        when:
        def result = runWithArguments('tasks', '-S')

        then:
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_STARTED'")
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_SCAN_URL:${mockScansServer.address}s/${PUBLIC_BUILD_SCAN_ID}'")
    }

    def "no service messages emitted when build scan plugin not applied in project build file"() {
        given:
        gradleVersion = GradleVersion.version('5.6.4')

        and:
        buildFile << """
            plugins {
                id 'nu.studer.build-scan.teamcity'
            }
"""

        when:
        def result = runWithArguments('tasks', '-S')

        then:
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_STARTED'")
        !result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_SCAN_URL:${mockScansServer.address}s/${PUBLIC_BUILD_SCAN_ID}'")
    }

    void "service messages emitted when Gradle Enterprise plugin applied in settings build file"() {
        given:
        gradleVersion = GradleVersion.version('6.0.1')
        addToTestKitRunnerPluginClasspath(GRADLE_ENTERPRISE_PLUGIN_CLASSPATH_SYS_PROP)

        and:
        settingsFile << """
buildscript {
    dependencies {
        classpath files(${implClasspath()})
    }
}

plugins {
  id 'com.gradle.enterprise' version '3.1.1'
}

apply plugin: 'nu.studer.build-scan.teamcity'

gradleEnterprise {
    buildScan {
       server = '${mockScansServer.address}'
       publishAlways()
    }
}
"""

        when:
        def result = runWithArguments('tasks', '-S')

        then:
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_STARTED'")
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_SCAN_URL:${mockScansServer.address}s/${PUBLIC_BUILD_SCAN_ID}'")
    }

    void "no service messages emitted when Gradle Enterprise plugin not applied in settings build file"() {
        given:
        gradleVersion = GradleVersion.version('6.0.1')

        and:
        settingsFile << """
buildscript {
    dependencies {
        classpath files(${implClasspath()})
    }
}

apply plugin: 'nu.studer.build-scan.teamcity'
"""

        when:
        def result = runWithArguments('tasks', '-S')

        then:
        result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_STARTED'")
        !result.output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_SCAN_URL:${mockScansServer.address}s/${PUBLIC_BUILD_SCAN_ID}'")
    }

    private def implClasspath() {
        def properties = new Properties()
        def resource = getClass().classLoader.getResource('plugin-under-test-metadata.properties')
        resource.withInputStream { stream -> properties.load(stream) }
        def implClasspath = properties.get('implementation-classpath')
        implClasspath.split(File.pathSeparator).collect { it.replace('\\', '\\\\') }.collect { "'$it'" }.join(",")
    }

    protected void addToTestKitRunnerPluginClasspath(String classpathSystemPropertyName) {
        testKitRunnerPluginClasspath << new File(System.getProperty(classpathSystemPropertyName))
    }

}
