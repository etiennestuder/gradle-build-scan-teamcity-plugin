package nu.studer.gradle.buildscan.teamcity;

import com.gradle.scan.plugin.BuildScanExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;

import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class TeamCityBuildScanPlugin implements Plugin<Project> {

    private static final String TEAMCITY_VERSION_ENV = "TEAMCITY_VERSION";
    private static final String GRADLE_BUILDSCAN_TEAMCITY_PLUGIN_ENV = "GRADLE_BUILDSCAN_TEAMCITY_PLUGIN";
    private static final String BUILD_SCAN_EXTENSION = "com.gradle.scan.plugin.BuildScanExtension";
    private static final String BUILD_SCAN_PLUGIN_ID = "com.gradle.build-scan";
    private static final String BUILD_SCAN_SERVICE_MESSAGE_NAME = "nu.studer.teamcity.buildscan.buildScanLifeCycle";
    private static final String BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT = "BUILD_STARTED";
    private static final String BUILD_SCAN_SERVICE_URL_MESSAGE_ARGUMENT_PREFIX = "BUILD_SCAN_URL:";

    @Override
    public void apply(Project project) {
        // only register callback if this is a TeamCity build, and we are _not_ using the Gradle build runner
        if (System.getenv(TEAMCITY_VERSION_ENV) != null && System.getenv(GRADLE_BUILDSCAN_TEAMCITY_PLUGIN_ENV) == null) {
            project.getLogger().quiet(ServiceMessage.of(BUILD_SCAN_SERVICE_MESSAGE_NAME, BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT).toString());

            try {
                ExtensionContainer extensions = project.getExtensions();
                Class<?> buildScanExtensionClass = Class.forName(BUILD_SCAN_EXTENSION);
                if (extensions.findByType(buildScanExtensionClass) != null) {
                    maybeAddBuildScanPublishedHook(project);
                } else {
                    // The Gradle Enterprise Gradle plugin or the build scan plugin is available on the classpath,
                    // but has not been applied yet.
                    // This rules out the possibility of the plugin being applied as a Settings plugin.
                    // Register a callback if the plugin gets applied as a Project plugin later on
                    registerBuildScanPluginApplicationCallback(project);
                }
            } catch (ClassNotFoundException ignore) {
                // Neither the Gradle Enterprise Gradle plugin nor the build scan plugin is available on the classpath.
                // This rules out the possibility of the plugin being applied as a Settings plugin.
                // Register a callback if the plugin gets applied as a Project plugin later on
                registerBuildScanPluginApplicationCallback(project);
            }
        }
    }

    private static void registerBuildScanPluginApplicationCallback(Project project) {
        project.getPluginManager().withPlugin(BUILD_SCAN_PLUGIN_ID, appliedPlugin -> maybeAddBuildScanPublishedHook(project));
    }

    private static void maybeAddBuildScanPublishedHook(Project project) {
        BuildScanExtension buildScanExtension = project.getExtensions().getByType(BuildScanExtension.class);
        if (supportsScanPublishedListener(buildScanExtension)) {
            buildScanExtension.buildScanPublished(publishedBuildScan -> {
                        ServiceMessage serviceMessage = ServiceMessage.of(
                                BUILD_SCAN_SERVICE_MESSAGE_NAME,
                                BUILD_SCAN_SERVICE_URL_MESSAGE_ARGUMENT_PREFIX + publishedBuildScan.getBuildScanUri().toString()
                        );
                        project.getLogger().quiet(serviceMessage.toString());
                    }
            );
        }
    }

    private static boolean supportsScanPublishedListener(BuildScanExtension extension) {
        Class<?> clazz = extension.getClass();
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals("buildScanPublished")) {
                return true;
            }
        }
        return false;
    }

}
