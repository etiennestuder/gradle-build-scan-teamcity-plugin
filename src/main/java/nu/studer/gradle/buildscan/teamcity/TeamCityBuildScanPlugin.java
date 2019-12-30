package nu.studer.gradle.buildscan.teamcity;

import com.gradle.scan.plugin.BuildScanExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.util.GradleVersion;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class TeamCityBuildScanPlugin implements Plugin<Object> {

    private static final String TEAMCITY_VERSION_ENV = "TEAMCITY_VERSION";
    private static final String GRADLE_BUILDSCAN_TEAMCITY_PLUGIN_ENV = "GRADLE_BUILDSCAN_TEAMCITY_PLUGIN";
    private static final String BUILD_SCAN_PLUGIN_ID = "com.gradle.build-scan";
    private static final String BUILD_SCAN_SERVICE_MESSAGE_NAME = "nu.studer.teamcity.buildscan.buildScanLifeCycle";
    private static final String BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT = "BUILD_STARTED";
    private static final String BUILD_SCAN_SERVICE_URL_MESSAGE_ARGUMENT_PREFIX = "BUILD_SCAN_URL:";

    @Override
    public void apply(@Nonnull Object object) {
        // abort if old Gradle version is not supported
        if (GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("5.0")) < 0) {
            throw new IllegalStateException("This version of the TeamCity build scan plugin is not compatible with Gradle < 5.0");
        }

        // do not register callback if this is not a TeamCity build or we are using the TeamCity Gradle build runner with the TC build scan plugin applied
        if (System.getenv(TEAMCITY_VERSION_ENV) == null || System.getenv(GRADLE_BUILDSCAN_TEAMCITY_PLUGIN_ENV) != null) {
            return;
        }

        // handle plugin application to settings file and project file
        if (object instanceof Settings) {
            Settings settings = (Settings) object;
            throw new IllegalStateException("The TeamCity build scan plugin can currently only be applied to Project instances");
        } else if (object instanceof Project) {
            Project project = (Project) object;
            init(project);
        } else {
            throw new IllegalStateException("The TeamCity build scan plugin can only be applied to Settings and Project instances");
        }
    }

    private void init(Project project) {
        project.getLogger().quiet(ServiceMessage.of(BUILD_SCAN_SERVICE_MESSAGE_NAME, BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT).toString());

        project.getPluginManager().withPlugin(BUILD_SCAN_PLUGIN_ID, appliedPlugin -> {
            BuildScanExtension buildScanExtension = project.getExtensions().getByType(BuildScanExtension.class);
            if (supportsBuildScanPublishedListener(buildScanExtension)) {
                buildScanExtension.buildScanPublished(publishedBuildScan -> {
                        ServiceMessage serviceMessage = ServiceMessage.of(
                            BUILD_SCAN_SERVICE_MESSAGE_NAME,
                            BUILD_SCAN_SERVICE_URL_MESSAGE_ARGUMENT_PREFIX + publishedBuildScan.getBuildScanUri().toString()
                        );
                        project.getLogger().quiet(serviceMessage.toString());
                    }
                );
            }
        });
    }

    private static boolean supportsBuildScanPublishedListener(BuildScanExtension extension) {
        Class<?> clazz = extension.getClass();
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals("buildScanPublished")) {
                return true;
            }
        }
        return false;
    }

}
