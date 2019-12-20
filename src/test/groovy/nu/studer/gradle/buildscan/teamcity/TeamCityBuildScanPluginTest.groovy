package nu.studer.gradle.buildscan.teamcity

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.util.zip.GZIPOutputStream

class TeamCityBuildScanPluginTest extends Specification {

    public static final String PUBLIC_SCAN_ID = "i2wepy2gr7ovw"
    public static final String BUILD_SCAN_PLUGIN_CLASSPATH = 'buildScanPluginClasspath'
    public static final String GRADLE_ENTERPRISE_PLUGIN_CLASSPATH = 'gradleEnterprisePluginClasspath'

    @Rule
    TemporaryFolder projectDir = new TemporaryFolder()

    File buildScript

    GradleRunner runner

    List<File> testKitRunnerPluginClasspath

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
                .withProjectDir(projectDir.root).withDebug(true)
        testKitRunnerPluginClasspath = PluginUnderTestMetadataReading.readImplementationClasspath()
    }

    def "service messages emitted for compatible build scan plugin versions"() {
        given:
        addToTestKitRunnerPluginClasspath(BUILD_SCAN_PLUGIN_CLASSPATH)
        buildScript << """
            plugins {
                id 'com.gradle.build-scan' version '1.16'
                id 'nu.studer.build-scan.teamcity'
            }
        """.stripIndent()
        configureBuildScanPlugin()

        when:
        def result = run("tasks", "-S")

        then:
        outputContainsExpectedMessage(result.output)
    }

    def "service messages emitted when build scan plugin is applied later than the plugin"() {
        given:
        addToTestKitRunnerPluginClasspath(BUILD_SCAN_PLUGIN_CLASSPATH)
        buildScript << """
            plugins {
                id 'nu.studer.build-scan.teamcity'
                id 'com.gradle.build-scan' version '1.16'
            }
        """.stripIndent()
        configureBuildScanPlugin()

        when:
        def result = run("tasks", "-S")

        then:
        outputContainsExpectedMessage(result.output)
    }

    def "service messages emitted for compatible Gradle Enterprise plugin versions"() {
        given:
        runner.withGradleVersion("6.0")
        addToTestKitRunnerPluginClasspath(GRADLE_ENTERPRISE_PLUGIN_CLASSPATH)
        applyGradleBuildScanTeamCityPlugin()
        applyGradleEnterprisePlugin("3.0")

        when:
        def result = run("tasks", "-S")

        then:
        outputContainsExpectedMessage(result.output)
    }

    @Unroll
    def "no service messages emitted when build scan or Gradle Enterprise plugin is not applied (#pluginClasspath)"() {
        given:
        addToTestKitRunnerPluginClasspath(pluginClasspath)
        applyGradleBuildScanTeamCityPlugin()

        when:
        def result = run("tasks", "-S")

        then:
        !outputContainsExpectedMessage(result.output)

        where:
        pluginClasspath << [BUILD_SCAN_PLUGIN_CLASSPATH, GRADLE_ENTERPRISE_PLUGIN_CLASSPATH]
    }

    def "no service messages emitted when build scan or Gradle Enterprise plugin are not on the classpath"() {
        given:
        applyGradleBuildScanTeamCityPlugin()

        when:
        def result = run("tasks", "-S")

        then:
        !outputContainsExpectedMessage(result.output)
    }

    private void addToTestKitRunnerPluginClasspath(String classpathSystemPropertyName) {
        testKitRunnerPluginClasspath << new File(System.getProperty(classpathSystemPropertyName))
    }

    private BuildResult run(String... args) {
        runner
                .withPluginClasspath(testKitRunnerPluginClasspath)
                .withArguments(args)
                .build()
    }

    private void configureBuildScanPlugin() {
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
    }

    private void applyGradleBuildScanTeamCityPlugin() {
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
