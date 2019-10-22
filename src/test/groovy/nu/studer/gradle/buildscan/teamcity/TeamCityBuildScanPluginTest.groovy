package nu.studer.gradle.buildscan.teamcity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.zip.GZIPOutputStream

class TeamCityBuildScanPluginTest extends Specification {

    public static final String PUBLIC_SCAN_ID = "i2wepy2gr7ovw"
    public static final String BUILD_SCAN_PLUGIN_CLASSPATH = 'buildScanPluginClasspath'
    public static final String GRADLE_ENTERPRISE_PLUGIN_CLASSPATH = 'gradleEnterprisePluginClasspath'

    @Rule
    TemporaryFolder projectDir = new TemporaryFolder()

    File buildScript

    GradleRunner runner

    @AutoCleanup
    def mockScansServer = GroovyEmbeddedApp.of {
        def objectMapper = new ObjectMapper(new SmileFactory())

        handlers {
            post("in/:gradleVersion/:pluginVersion") { ctx ->
                def scanUrlString = "${mockScansServer.address}s/" + PUBLIC_SCAN_ID
                def os = new ByteArrayOutputStream()

                new GZIPOutputStream(os).withCloseable { stream ->
                    def generator = objectMapper.getFactory().createGenerator(stream)
                    generator.writeStartObject()
                    generator.writeFieldName("id")
                    generator.writeString(PUBLIC_SCAN_ID)
                    generator.writeFieldName("scanUrl")
                    generator.writeString(scanUrlString)
                    generator.writeEndObject()
                    generator.close()
                }

                response.contentType("application/vnd.gradle.scan-ack").send(os.toByteArray())
            }
        }
    }

    def setup() {
        buildScript = projectDir.newFile("build.gradle")
        runner = GradleRunner.create()
                .withProjectDir(projectDir.root)
    }

    def "service messages emitted for compatible build scan plugin versions"() {
        given:
        configurePluginClasspath(BUILD_SCAN_PLUGIN_CLASSPATH)
        buildScript << """
            plugins {
                id 'com.gradle.build-scan' version '1.16'
                id 'nu.studer.build-scan.teamcity'
            }
        """.stripIndent()
        configureScanPlugin()

        when:
        def result = runner.withArguments("tasks", "-S").build()

        then:
        outputContainsExpectedMessage(result.output)
    }

    def "service messages emitted when build scan plugin is applied later than the plugin"() {
        given:
        configurePluginClasspath(BUILD_SCAN_PLUGIN_CLASSPATH)
        buildScript << """
            plugins {
                id 'nu.studer.build-scan.teamcity'
                id 'com.gradle.build-scan' version '1.16'
            }
        """.stripIndent()
        configureScanPlugin()

        when:
        def result = runner.withArguments("tasks", "-S").build()

        then:
        outputContainsExpectedMessage(result.output)
    }

    def "service messages emitted for compatible Gradle Enterprise plugin versions"() {
        given:
        runner.withGradleVersion("6.0-rc-1") // Gradle 6.0 is required for GE plugin 3.0
        configurePluginClasspath(GRADLE_ENTERPRISE_PLUGIN_CLASSPATH)
        applyGradleEnterprisePlugin("3.0")

        when:
        def result = runner.withArguments("tasks", "-S").build()

        then:
        outputContainsExpectedMessage(result.output)
    }

    def "no service messages emitted when build scan or Gradle Enterprise plugin is not applied"() {
        given:
        configurePluginClasspath(BUILD_SCAN_PLUGIN_CLASSPATH)
        applyPlugin()

        when:
        def result = runner.withArguments("tasks", "-S").build()

        then:
        !outputContainsExpectedMessage(result.output)
    }

    def "no service messages emitted when build scan or Gradle Enterprise plugin are not on the classpath"() {
        given:
        configurePluginClasspath()
        applyPlugin()

        when:
        def result = runner.withArguments("tasks", "-S").build()

        then:
        !outputContainsExpectedMessage(result.output)
    }

    private void configurePluginClasspath(String classpathSystemPropertyName = null) {
        runner.withPluginClasspath(PluginUnderTestMetadataReading.readImplementationClasspath() + (classpathSystemPropertyName ? [new File(System.getProperty(classpathSystemPropertyName))] : []))
    }

    private void configureScanPlugin() {
        buildScript << """
            buildScan {
                server = '${mockScansServer.address}'
                publishAlways()
            }
        """.stripIndent()
    }

    private void applyGradleEnterprisePlugin(String version) {
        projectDir.newFile("settings.gradle") << """ 
            plugins {
                id 'com.gradle.enterprise' version '${version}'
            }
            
            apply plugin: "com.gradle.enterprise"
            gradleEnterprise {
                buildScan {
                    server = '${mockScansServer.address}'
                    publishAlways()
                }
            }
        """.stripIndent()
        applyPlugin()
    }

    private void applyPlugin() {
        buildScript << """
            plugins {
                id 'nu.studer.build-scan.teamcity'
            }
        """.stripIndent()
    }

    private boolean outputContainsExpectedMessage(String output) {
        output.contains("##teamcity[nu.studer.teamcity.buildscan.buildScanLifeCycle 'BUILD_SCAN_URL:${mockScansServer.address}s/${PUBLIC_SCAN_ID}'")
    }

}
