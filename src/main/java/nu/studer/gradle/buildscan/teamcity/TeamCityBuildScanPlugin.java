package nu.studer.gradle.buildscan.teamcity;

import com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension;
import com.gradle.scan.plugin.BuildScanExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.GradleVersion;

import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public class TeamCityBuildScanPlugin implements Plugin<Object> {

    private static final String TEAMCITY_VERSION_ENV = "TEAMCITY_VERSION";
    private static final String GRADLE_BUILDSCAN_TEAMCITY_PLUGIN_ENV = "GRADLE_BUILDSCAN_TEAMCITY_PLUGIN";
    private static final String BUILD_SCAN_PLUGIN_ID = "com.gradle.build-scan";
    private static final String GRADLE_ENTERPRISE_PLUGIN_ID = "com.gradle.enterprise";
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
            init(settings);
        } else if (object instanceof Project) {
            Project project = (Project) object;
            init(project);
        } else {
            throw new IllegalStateException("The TeamCity build scan plugin can only be applied to Settings and Project instances");
        }
    }

    private void init(Settings settings) {
        settings.getGradle().settingsEvaluated(s -> {
                Logger logger = Logging.getLogger(Settings.class);
                logger.quiet(ServiceMessage.of(BUILD_SCAN_SERVICE_MESSAGE_NAME, BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT).toString());
                settings.getPluginManager().withPlugin(GRADLE_ENTERPRISE_PLUGIN_ID, appliedPlugin -> SettingsApplication.apply(settings, logger));
            }
        );
    }

    private void init(Project project) {
        Logger logger = Logging.getLogger(Project.class);
        logger.quiet(ServiceMessage.of(BUILD_SCAN_SERVICE_MESSAGE_NAME, BUILD_SCAN_SERVICE_STARTED_MESSAGE_ARGUMENT).toString());
        project.getPluginManager().withPlugin(BUILD_SCAN_PLUGIN_ID, appliedPlugin -> ProjectApplication.apply(project, logger));
    }

    private static final class SettingsApplication {

        private static void apply(Settings settings, Logger logger) {
            BuildScanExtension buildScanExtension = settings.getExtensions().getByType(GradleEnterpriseExtension.class).getBuildScan();
            BaseApplication.registerListener(buildScanExtension, logger);
        }

    }

    private static final class ProjectApplication {

        private static void apply(Project project, Logger logger) {
            BuildScanExtension buildScanExtension = project.getExtensions().getByType(BuildScanExtension.class);
            BaseApplication.registerListener(buildScanExtension, logger);
        }

    }

    private static final class BaseApplication {

        private static void registerListener(BuildScanExtension buildScanExtension, Logger logger) {
            buildScanExtension.buildScanPublished(publishedBuildScan -> {
                    ServiceMessage serviceMessage = ServiceMessage.of(
                        TeamCityBuildScanPlugin.BUILD_SCAN_SERVICE_MESSAGE_NAME,
                        TeamCityBuildScanPlugin.BUILD_SCAN_SERVICE_URL_MESSAGE_ARGUMENT_PREFIX + publishedBuildScan.getBuildScanUri().toString()
                    );
                    logger.quiet(serviceMessage.toString());
                }
            );
        }

        private BaseApplication() {
        }

    }

}
