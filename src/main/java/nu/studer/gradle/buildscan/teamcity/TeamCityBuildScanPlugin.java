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
    private static final String BUILD_SCAN_PLUGIN_ID = "com.gradle.build-scan";
    private static final String BUILD_SCAN_SERVICE_MESSAGE_NAME = "nu.studer.teamcity.buildscan.buildScanLifeCycle";
    private static final String BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT = "BUILD_STARTED";
    private static final String BUILD_SCAN_SERVICE_URL_MESSAGE_ARGUMENT_PREFIX = "BUILD_SCAN_URL:";

    @Override
    public void apply(Project project) {
        // only register callback if this is a TeamCity build, and we are _not_ using the Gradle build runner
        if (System.getenv(TEAMCITY_VERSION_ENV) != null && System.getenv(GRADLE_BUILDSCAN_TEAMCITY_PLUGIN_ENV) == null) {
            project.getLogger().quiet(ServiceMessage.of(BUILD_SCAN_SERVICE_MESSAGE_NAME, BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT).toString());

            ExtensionContainer extensions = project.getExtensions();
            BuildScanExtension buildScanExtension = extensions.findByType(BuildScanExtension.class);
            if (buildScanExtension != null) {
                registerCallback(project);
                return;
            }
            project.getPluginManager().withPlugin(BUILD_SCAN_PLUGIN_ID, appliedPlugin -> registerCallback(project));
        }
    }

    private static void registerCallback(Project project) {
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
        Class clazz = extension.getClass();
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals("buildScanPublished")) {
                return true;
            }
        }
        return false;
    }

}
