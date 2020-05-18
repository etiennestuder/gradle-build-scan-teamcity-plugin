package nu.studer.gradle.buildscan.teamcity

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.gradle.util.GradleVersion
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import spock.lang.AutoCleanup

import java.util.zip.GZIPOutputStream

class TeamCityBuildScanPluginTest extends BaseFuncTest {

    static final String PUBLIC_BUILD_SCAN_ID = "i2wepy2gr7ovw"
    static final String DEFAULT_SCAN_UPLOAD_TOKEN = 'scan-upload-token'

    static final String BUILD_SCAN_PLUGIN_CLASSPATH_SYS_PROP = 'buildScanPluginClasspath'
    static final String GRADLE_ENTERPRISE_PLUGIN_CLASSPATH_SYS_PROP = 'gradleEnterprisePluginClasspath'


    @AutoCleanup
    def mockScansServer = GroovyEmbeddedApp.of {
        def jsonWritter = new ObjectMapper(new JsonFactory()).writer()
        def objectMapper = new ObjectMapper(new SmileFactory())
        handlers {
            prefix('scans/publish') {
                post('gradle/:pluginVersion/token') {
                    def pluginVersion = context.pathTokens.pluginVersion
                    def scanUrlString = "${mockScansServer.address}s/" + PUBLIC_BUILD_SCAN_ID
                    def body = [
                            id: PUBLIC_BUILD_SCAN_ID,
                            scanUrl: scanUrlString.toString(),
                            scanUploadUrl: "${mockScansServer.address.toString()}scans/publish/gradle/$pluginVersion/upload".toString(),
                            scanUploadToken: DEFAULT_SCAN_UPLOAD_TOKEN
                    ]
                    context.response
                            .contentType('application/vnd.gradle.scan-ack+json')
                            .send(jsonWritter.writeValueAsBytes(body))
                }
                post('gradle/:pluginVersion/upload') {
                    context.request.getBody(1024 * 1024 * 10).then {
                        context.response
                                .contentType('application/vnd.gradle.scan-upload-ack+json')
                                .send()
                    }
                }
                notFound()
            }
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
                id 'com.gradle.build-scan' version '3.3.1'
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
                id 'com.gradle.build-scan' version '3.3.1'
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
  id 'com.gradle.enterprise' version '3.3.1'
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

    private static def implClasspath() {
        PluginUnderTestMetadataReading.readImplementationClasspath().collect { it.absolutePath.replace('\\', '\\\\') }.collect { "'$it'" }.join(",")
    }

    protected void addToTestKitRunnerPluginClasspath(String classpathSystemPropertyName) {
        testKitRunnerPluginClasspath << new File(System.getProperty(classpathSystemPropertyName))
    }

}
